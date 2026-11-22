package ru.archzar.nearfieldstorage;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.nfc.FormatException;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import ru.archzar.nearfieldstorage.nfc.NfcVault;
import ru.archzar.nearfieldstorage.utilities.VaultParts;

public class MainActivity extends AppCompatActivity {
    private enum Screen { HOME, ENCODED, DECODED, EDIT, WRITE, CLEAR }

    public static final class PendingOperations extends ViewModel {
        private NfcVault.WriteSession session;
        private boolean loaded;
        private final MutableLiveData<Boolean> exportingDump = new MutableLiveData<>(false);
        private boolean writePaused;
        private boolean openingDump;
        private final ExecutorService documents = Executors.newSingleThreadExecutor();
        private final MutableLiveData<DumpResult> importResult = new MutableLiveData<>();
        private final MutableLiveData<NfcVault.WriteSession> progress = new MutableLiveData<>();

        @Override
        protected void onCleared() {
            documents.shutdownNow();
        }
    }

    private static final int READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A
            | NfcAdapter.FLAG_READER_NFC_B | NfcAdapter.FLAG_READER_NFC_F
            | NfcAdapter.FLAG_READER_NFC_V;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService crypto = Executors.newSingleThreadExecutor();
    private final List<NfcVault.Snapshot> source = new ArrayList<>();
    private VaultParts.Assembly assembly = new VaultParts.Assembly();
    private volatile Scan scan;
    private NfcVault.ClearSession clearing;
    private NfcAdapter adapter;
    private PendingOperations pending;
    private ActivityResultLauncher<String[]> openDocument;
    private ActivityResultLauncher<String> createDocument;
    private Screen screen;
    private boolean active;
    private boolean stopped;
    private boolean cryptBusy;
    private int generation;
    private String plaintext;
    private String draft = "";
    private int tagCount = 1;
    private AlertDialog dialog;
    private final BroadcastReceiver nfcState = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (active && (!adapter.isEnabled() || scan == null)) {
                syncReader();
                refreshScanScreen();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_main);
        View container = findViewById(R.id.coordinator);
        if (Build.VERSION.SDK_INT >= 30) {
            container.setImportantForContentCapture(View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS);
        }
        ViewCompat.setOnApplyWindowInsetsListener(container, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return windowInsets;
        });
        adapter = NfcAdapter.getDefaultAdapter(this);
        pending = new ViewModelProvider(this).get(PendingOperations.class);
        openDocument = registerForActivityResult(new ActivityResultContracts.OpenDocument() {
            @NonNull
            @Override
            public Intent createIntent(@NonNull Context context, @NonNull String[] input) {
                return super.createIntent(context, input).addCategory(Intent.CATEGORY_OPENABLE);
            }
        }, this::onDumpSelected);
        createDocument = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("text/plain"), this::saveDumpTo);
        if (savedInstanceState == null && !isExporting()) {
            exportFile().delete();
        }
        if (!pending.loaded) {
            pending.loaded = true;
            try {
                pending.session = NfcVault.WriteSession.restore(checkpointFile());
                if (pending.session != null && pending.session.completed() < pending.session.count()) {
                    pending.writePaused = exportFile().exists();
                    toast(R.string.resuming_write);
                }
            } catch (IOException error) {
                toast(R.string.recovery_failed);
            }
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                back();
            }
        });
        show(pending.session == null ? Screen.HOME : Screen.WRITE);
        pending.importResult.observe(this, ignored -> finishImport());
        pending.exportingDump.observe(this, ignored -> {
            if (active && screen == Screen.WRITE) {
                syncReader();
                refreshScanScreen();
            }
        });
        pending.progress.observe(this, session -> {
            if (active && screen == Screen.WRITE && pending.session == session) {
                if (session.completed() == session.count()) {
                    written();
                } else {
                    refreshScanScreen();
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (adapter != null) {
            ContextCompat.registerReceiver(this, nfcState,
                    new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
                    ContextCompat.RECEIVER_EXPORTED);
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        active = true;
        if (screen == Screen.WRITE && pending.session.completed() == pending.session.count()) {
            written();
        } else if (screen == Screen.CLEAR && clearing != null && clearing.completed()) {
            tagCleared();
        } else if (screen == Screen.CLEAR && clearing != null && clearing.isWriting()) {
            refreshScanScreen();
        } else if (stopped && pending.session == null) {
            show(Screen.HOME);
        } else {
            refreshScanScreen();
            syncReader();
        }
        stopped = false;
        finishImport();
    }

    @Override
    protected void onPause() {
        active = false;
        syncReader();
        super.onPause();
    }

    @Override
    protected void onStop() {
        stopped = true;
        if (adapter != null) {
            unregisterReceiver(nfcState);
        }
        if (dialog != null) {
            dialog.dismiss();
        }
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment);
        if (fragment instanceof EditAndChangeFragment) {
            ((EditAndChangeFragment) fragment).clearFields();
        } else if (fragment instanceof DecodedInformationFragment) {
            ((DecodedInformationFragment) fragment).clearText();
        }
        clearUnlocked();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        worker.shutdown();
        crypto.shutdown();
        super.onDestroy();
    }

    private void show(Screen target) {
        screen = target;
        if (target != Screen.CLEAR) {
            clearing = null;
        }
        Fragment fragment;
        switch (target) {
            case ENCODED: fragment = new EncodedInformationFragment(); break;
            case DECODED: fragment = new DecodedInformationFragment(); break;
            case EDIT: fragment = new EditAndChangeFragment(); break;
            case WRITE:
            case CLEAR: fragment = new AttachTagFragment(); break;
            default: fragment = new MainMenuFragment(); break;
        }
        getSupportFragmentManager().beginTransaction().setReorderingAllowed(true)
                .replace(R.id.fragment, fragment).commit();
        syncReader();
    }

    private void syncReader() {
        if (scan != null) {
            scan = null;
            adapter.disableReaderMode(this);
        }
        if (active && dialog == null && !pending.openingDump
                && adapter != null && adapter.isEnabled()
                && (screen == Screen.HOME
                        || (screen == Screen.WRITE && !isExporting() && !pending.writePaused
                                && pending.session.writeProblem() == null)
                        || (screen == Screen.CLEAR && (clearing == null
                                || (!clearing.completed() && !clearing.isWriting()))))) {
            Scan request = new Scan(screen == Screen.WRITE ? pending.session : null,
                    screen == Screen.CLEAR, clearing);
            scan = request;
            adapter.enableReaderMode(this, tag -> scanTag(request, tag), READER_FLAGS, null);
        }
    }

    private void scanTag(Scan request, Tag tag) {
        if (scan != request || !request.busy.compareAndSet(false, true)) {
            return;
        }
        try {
            worker.execute(() -> {
                try {
                    if (scan != request) {
                        return;
                    }
                    if (request.clear != null) {
                        request.clear.clear(tag, () -> scan == request);
                    } else if (request.clearMode) {
                        NfcVault.ClearSession candidate = NfcVault.ClearSession.identify(tag);
                        runOnUiThread(() -> confirmClearTag(request, candidate));
                    } else if (request.write == null) {
                        NfcVault.Snapshot snapshot = NfcVault.read(tag);
                        runOnUiThread(() -> finishRead(request, snapshot));
                    } else {
                        request.write.writeNext(tag, () -> scan == request);
                    }
                } catch (NfcVault.Problem error) {
                    nfcError(request, error.message, error.arguments);
                } catch (TagLostException error) {
                    nfcError(request, R.string.tag_lost);
                } catch (FormatException error) {
                    nfcError(request, R.string.bad_tag);
                } catch (IOException | SecurityException error) {
                    nfcError(request, R.string.nfc_retry);
                } finally {
                    if (request.write != null) {
                        pending.progress.postValue(request.write);
                    }
                    runOnUiThread(() -> {
                        request.busy.set(false);
                        if (active && request.clear != null && request.clear == clearing) {
                            if (request.clear.completed()) {
                                tagCleared();
                            } else if (scan == null) {
                                syncReader();
                            }
                        }
                    });
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException closed) {
            request.busy.set(false);
        }
    }

    private void finishRead(Scan request, NfcVault.Snapshot snapshot) {
        if (scan != request) {
            return;
        }
        VaultParts.Part part;
        try {
            part = snapshot.part();
        } catch (IllegalArgumentException error) {
            toast(R.string.bad_tag);
            return;
        }
        try {
            if (source.stream().anyMatch(snapshot::sameTag)) {
                throw new IllegalArgumentException("Duplicate tag");
            }
            assembly.add(part);
        } catch (IllegalArgumentException error) {
            toast(R.string.wrong_read_tag, assembly.nextNumber());
            return;
        }
        source.add(snapshot);
        if (assembly.isComplete()) {
            show(Screen.ENCODED);
        } else {
            refreshScanScreen();
        }
    }

    private void nfcError(Scan request, int message, Object... args) {
        runOnUiThread(() -> {
            if (scan == request || (active && request.clear != null && request.clear == clearing)) {
                if (request.write != null && request.write.completed() == request.write.count()) {
                    written();
                } else if (request.clear == null || !request.clear.completed()) {
                    toast(message, args);
                    refreshScanScreen();
                }
            }
        });
    }

    private void written() {
        toast(R.string.written, pending.session.count());
        home();
    }

    void startClearTag() {
        if (screen != Screen.HOME || pending.openingDump) {
            return;
        }
        if (adapter == null) {
            toast(R.string.nfc_unavailable);
            return;
        }
        clearUnlocked();
        show(Screen.CLEAR);
    }

    private void confirmClearTag(Scan request, NfcVault.ClearSession candidate) {
        if (scan != request) {
            return;
        }
        AlertDialog confirm = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_tag)
                .setMessage(R.string.clear_warning)
                .setNegativeButton(android.R.string.cancel, (ignored, which) -> home())
                .setPositiveButton(R.string.clear_continue, (ignored, which) -> {
                    clearing = candidate;
                    refreshScanScreen();
                }).create();
        confirm.setOnCancelListener(ignored -> home());
        if (showDialog(confirm)) {
            confirm.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(MaterialColors.getColor(
                    confirm.getButton(AlertDialog.BUTTON_POSITIVE),
                    com.google.android.material.R.attr.colorError));
        }
    }

    private void tagCleared() {
        toast(R.string.tag_cleared);
        home();
    }

    boolean isClearingTag() {
        return screen == Screen.CLEAR;
    }

    private int readHint() {
        if (adapter == null) {
            return R.string.nfc_unavailable;
        }
        if (!adapter.isEnabled()) {
            return R.string.nfc_disabled;
        }
        return assembly.count() == 0 ? R.string.tap_to_read : R.string.next_read_tag;
    }

    String readPrompt() {
        return getString(readHint(), assembly.nextNumber(), assembly.count());
    }

    String writePrompt() {
        if (screen == Screen.WRITE && pending.writePaused) {
            return getString(isExporting() ? R.string.exporting_dump : R.string.write_paused);
        }
        if (screen == Screen.WRITE && pending.session.writeProblem() != null) {
            return getString(R.string.write_unavailable);
        }
        if (adapter == null) {
            return getString(R.string.nfc_unavailable);
        }
        if (!adapter.isEnabled()) {
            return getString(R.string.nfc_disabled_title);
        }
        return getString(screen == Screen.CLEAR && clearing != null
                ? R.string.tap_same_tag : R.string.tap_tag);
    }

    String writeHint() {
        if (screen == Screen.WRITE && pending.writePaused) {
            return isExporting() ? "" : getString(R.string.write_resume_hint);
        }
        if (screen == Screen.WRITE && pending.session.writeProblem() != null) {
            NfcVault.Problem problem = pending.session.writeProblem();
            return getString(problem.message, problem.arguments);
        }
        if (adapter == null) {
            return "";
        }
        if (!adapter.isEnabled()) {
            return getString(R.string.nfc_settings_hint);
        }
        if (screen == Screen.CLEAR) {
            return getString(clearing == null ? R.string.confirm_after_scan : R.string.hold_tag);
        }
        NfcVault.WriteSession session = pending.session;
        if (session == null || session.completed() == session.count()) {
            return "";
        }
        return getString(session.nextIsOriginal() ? R.string.next_original : R.string.next_blank,
                session.nextNumber(), session.count());
    }

    void tapBanner() {
        if (screen == Screen.WRITE && pending.writePaused) {
            if (!isExporting()) {
                pending.writePaused = false;
                syncReader();
                refreshScanScreen();
            }
        } else if (adapter == null) {
            toast(R.string.nfc_unavailable);
        } else if (!adapter.isEnabled()) {
            startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));
        } else if (screen == Screen.HOME && assembly.count() > 0) {
            home();
        } else if (scan == null) {
            syncReader();
        }
    }

    private void refreshScanScreen() {
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment);
        if (current instanceof MainMenuFragment) {
            ((MainMenuFragment) current).refresh();
        } else if (current instanceof AttachTagFragment) {
            ((AttachTagFragment) current).refresh();
        }
    }

    void openDump() {
        if (pending.openingDump || screen != Screen.HOME) {
            return;
        }
        pending.openingDump = true;
        syncReader();
        try {
            openDocument.launch(new String[]{"text/plain", "application/octet-stream"});
        } catch (ActivityNotFoundException error) {
            pending.openingDump = false;
            syncReader();
            toast(R.string.document_picker_unavailable);
        }
    }

    private void onDumpSelected(Uri uri) {
        if (uri == null) {
            pending.openingDump = false;
            syncReader();
            return;
        }
        pending.openingDump = true;
        syncReader();
        ContentResolver resolver = getApplicationContext().getContentResolver();
        MutableLiveData<DumpResult> result = pending.importResult;
        pending.documents.execute(() -> {
            VaultParts.Assembly imported;
            try (InputStream input = resolver.openInputStream(uri)) {
                if (input == null) {
                    throw new IOException("Missing document stream");
                }
                imported = VaultParts.readDump(input);
            } catch (IllegalArgumentException error) {
                result.postValue(new DumpResult(null, R.string.dump_invalid));
                return;
            } catch (IOException | SecurityException error) {
                result.postValue(new DumpResult(null, R.string.dump_open_failed));
                return;
            }
            result.postValue(new DumpResult(imported, 0));
        });
    }

    private void finishImport() {
        DumpResult result = pending.importResult.getValue();
        if (!active || result == null || getSupportFragmentManager().isStateSaved()) {
            return;
        }
        pending.importResult.setValue(null);
        pending.openingDump = false;
        if (result.assembly != null && screen == Screen.HOME) {
            clearUnlocked();
            assembly = result.assembly;
            show(Screen.ENCODED);
        } else {
            if (result.error != 0) {
                toast(result.error);
            }
            syncReader();
        }
    }

    void showExportMenu(View anchor) {
        if (!assembly.isComplete() || cryptBusy || isExporting()) {
            return;
        }
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(R.string.dump_save);
        menu.setOnMenuItemClickListener(item -> {
            if (assembly.isComplete() && !isExporting()) {
                pending.exportingDump.setValue(true);
                stageExport(assembly.toDump());
            }
            return true;
        });
        menu.show();
    }

    void exportPreparedWrite() {
        if (screen != Screen.WRITE || isExporting()) {
            return;
        }
        Scan request = scan;
        if (pending.session.isWriting()
                || (request != null && !request.busy.compareAndSet(false, true))) {
            toast(R.string.wait_for_write);
            return;
        }
        pending.writePaused = true;
        pending.exportingDump.setValue(true);
        try {
            stageExport(pending.session.toDump());
        } catch (NfcVault.Problem | IllegalArgumentException | IllegalStateException error) {
            pending.exportingDump.setValue(false);
            toast(R.string.dump_save_failed);
        }
    }

    boolean isExporting() {
        return Boolean.TRUE.equals(pending.exportingDump.getValue());
    }

    private void stageExport(String dump) {
        Screen origin = screen;
        int operation = generation;
        File file = exportFile();
        pending.documents.execute(() -> {
            try (OutputStream output = new FileOutputStream(file)) {
                output.write(dump.getBytes(StandardCharsets.UTF_8));
            } catch (IOException error) {
                runOnUiThread(() -> finishExport(file, R.string.dump_save_failed));
                return;
            }
            runOnUiThread(() -> {
                if (!active || operation != generation || screen != origin || isDestroyed()) {
                    finishExport(file, 0);
                    return;
                }
                try {
                    createDocument.launch("NFS-dump.txt");
                } catch (ActivityNotFoundException error) {
                    finishExport(file, R.string.document_picker_unavailable);
                }
            });
        });
    }

    private void saveDumpTo(Uri uri) {
        File file = exportFile();
        if (uri == null) {
            finishExport(file, 0);
            return;
        }
        pending.exportingDump.setValue(true);
        pending.documents.execute(() -> {
            try (InputStream input = new FileInputStream(file);
                 OutputStream output = getContentResolver().openOutputStream(uri)) {
                if (output == null) {
                    throw new IOException("Missing document stream");
                }
                byte[] buffer = new byte[4096];
                int size;
                while ((size = input.read(buffer)) != -1) {
                    output.write(buffer, 0, size);
                }
            } catch (IOException | SecurityException error) {
                runOnUiThread(() -> finishExport(file, R.string.dump_save_failed));
                return;
            }
            runOnUiThread(() -> finishExport(file, R.string.dump_saved));
        });
    }

    private void finishExport(File file, int message) {
        file.delete();
        pending.exportingDump.setValue(false);
        if (message != 0 && !isDestroyed()) {
            toast(message);
        }
    }

    private File exportFile() {
        return new File(getNoBackupFilesDir(), "nfs-pending-export.txt");
    }

    boolean hasEncryptedData() {
        return screen == Screen.ENCODED && assembly.isComplete();
    }

    String encryptedPreview() {
        if (!hasEncryptedData()) {
            return "";
        }
        byte[] data = assembly.encryptedBytes();
        int previewBytes = Math.min(data.length, 384);
        String preview = java.util.Base64.getEncoder().encodeToString(
                Arrays.copyOf(data, previewBytes));
        return previewBytes == data.length ? preview : preview + "…";
    }

    boolean unlock(char[] password) {
        if (!hasEncryptedData() || cryptBusy || password.length == 0) {
            return false;
        }
        cryptBusy = true;
        toast(R.string.decrypting);
        int operation = generation;
        VaultParts.Assembly reading = assembly;
        try {
            crypto.execute(() -> {
                String text;
                try {
                    try {
                        text = reading.decrypt(password);
                    } finally {
                        Arrays.fill(password, '\0');
                    }
                } catch (GeneralSecurityException error) {
                    cryptError(operation, R.string.unlock_failed);
                    return;
                }
                runOnUiThread(() -> {
                    if (generation == operation) {
                        cryptBusy = false;
                        if (!getSupportFragmentManager().isStateSaved()) {
                            plaintext = text;
                            show(Screen.DECODED);
                        }
                    }
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException closed) {
            cryptBusy = false;
            return false;
        }
        return true;
    }

    String unlockedText() {
        return plaintext == null ? "" : plaintext;
    }

    void newVault() {
        if (pending.openingDump) {
            toast(R.string.dump_loading);
            return;
        }
        clearUnlocked();
        show(Screen.EDIT);
    }

    void editVault() {
        if (isEditingExisting()) {
            draft = plaintext;
            tagCount = assembly.count();
            show(Screen.EDIT);
        }
    }

    boolean isEditingExisting() {
        return plaintext != null;
    }

    boolean hasOriginalTags() {
        return !source.isEmpty();
    }

    int minimumTagCount() {
        return Math.max(1, source.size());
    }

    int tagCount() {
        return tagCount;
    }

    void setTagCount(int count) {
        tagCount = count;
    }

    String draft() {
        return draft;
    }

    boolean prepareWrite(String text, char[] password) {
        if (screen != Screen.EDIT || cryptBusy || password.length == 0) {
            return false;
        }
        draft = text;
        cryptBusy = true;
        updateEditorPreparing();
        toast(R.string.encrypting);
        int operation = generation;
        int count = tagCount;
        File checkpoint = checkpointFile();
        List<NfcVault.Snapshot> originals = new ArrayList<>(source);
        try {
            crypto.execute(() -> {
                NfcVault.WriteSession session;
                try {
                    List<VaultParts.Part> parts;
                    try {
                        parts = VaultParts.create(text, password, count);
                    } finally {
                        Arrays.fill(password, '\0');
                    }
                    session = new NfcVault.WriteSession(checkpoint, parts, originals);
                } catch (IllegalArgumentException error) {
                    cryptError(operation, R.string.vault_too_large);
                    return;
                } catch (GeneralSecurityException error) {
                    cryptError(operation, R.string.encrypt_failed);
                    return;
                }
                runOnUiThread(() -> {
                    if (generation == operation) {
                        cryptBusy = false;
                        if (!getSupportFragmentManager().isStateSaved()) {
                            pending.session = session;
                            pending.writePaused = false;
                            WindowInsetsControllerCompat insets =
                                    ViewCompat.getWindowInsetsController(findViewById(R.id.coordinator));
                            if (insets != null) {
                                insets.hide(WindowInsetsCompat.Type.ime());
                            }
                            show(Screen.WRITE);
                        } else {
                            updateEditorPreparing();
                        }
                    }
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException closed) {
            cryptBusy = false;
            updateEditorPreparing();
            return false;
        }
        return true;
    }

    private void cryptError(int operation, int message) {
        runOnUiThread(() -> {
            if (generation == operation) {
                cryptBusy = false;
                updateEditorPreparing();
                toast(message);
            }
        });
    }

    boolean isPreparingWrite() {
        return screen == Screen.EDIT && cryptBusy;
    }

    private void updateEditorPreparing() {
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment);
        if (current instanceof EditAndChangeFragment) {
            ((EditAndChangeFragment) current).setPreparing(isPreparingWrite());
        }
    }

    boolean showDialog(AlertDialog next) {
        return showDialog(next, null);
    }

    boolean showDialog(AlertDialog next, Runnable onDismiss) {
        if (dialog != null || cryptBusy || !active) {
            return false;
        }
        dialog = next;
        dialog.setOnDismissListener(ignored -> {
            if (onDismiss != null) {
                onDismiss.run();
            }
            dialog = null;
            syncReader();
        });
        dialog.show();
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        syncReader();
        return true;
    }

    void back() {
        if (cryptBusy) {
            return;
        }
        switch (screen) {
            case HOME:
                if (assembly.count() == 0) {
                    finish();
                } else {
                    home();
                }
                break;
            case EDIT:
                if (isEditingExisting()) {
                    draft = "";
                    show(Screen.DECODED);
                } else {
                    home();
                }
                break;
            case WRITE:
                if (pending.session.isWriting() || (scan != null && scan.busy.get())) {
                    toast(R.string.wait_for_write);
                } else if (pending.session.hasStarted()) {
                    showDialog(new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.cancel_write)
                            .setMessage(R.string.partial_write_warning)
                            .setNegativeButton(R.string.keep_writing, null)
                            .setPositiveButton(R.string.cancel_write, (ignored, which) -> home())
                            .create());
                } else if (stopped || (draft.isEmpty() && !isEditingExisting())) {
                    home();
                } else {
                    pending.session = null;
                    show(Screen.EDIT);
                }
                break;
            case CLEAR:
                if ((clearing != null && clearing.isWriting()) || (scan != null && scan.busy.get())) {
                    toast(R.string.wait_for_clear);
                } else {
                    home();
                }
                break;
            default:
                home();
        }
    }

    private void home() {
        if (pending.session != null) {
            pending.session.discard();
        }
        pending.session = null;
        pending.writePaused = false;
        clearUnlocked();
        show(Screen.HOME);
    }

    private File checkpointFile() {
        return new File(getNoBackupFilesDir(), "nfs-pending-write");
    }

    private void clearUnlocked() {
        generation++;
        cryptBusy = false;
        plaintext = null;
        draft = "";
        tagCount = 1;
        source.clear();
        assembly = new VaultParts.Assembly();
    }

    private void toast(int message, Object... args) {
        Toast.makeText(this, getString(message, args), Toast.LENGTH_LONG).show();
    }

    private static final class Scan {
        final NfcVault.WriteSession write;
        final boolean clearMode;
        final NfcVault.ClearSession clear;
        final AtomicBoolean busy = new AtomicBoolean();

        Scan(NfcVault.WriteSession write, boolean clearMode, NfcVault.ClearSession clear) {
            this.write = write;
            this.clearMode = clearMode;
            this.clear = clear;
        }
    }

    private static final class DumpResult {
        final VaultParts.Assembly assembly;
        final int error;

        DumpResult(VaultParts.Assembly assembly, int error) {
            this.assembly = assembly;
            this.error = error;
        }
    }
}
