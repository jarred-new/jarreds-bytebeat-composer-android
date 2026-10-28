package com.jarredapps.bytebeatcomposer;

import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.jarredapps.bytebeatcomposer.databinding.MainBinding;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int FORMAT_UNKNOWN = 0;
    private static final int FORMAT_BB = 1;
    private static final int FORMAT_TEXT = 2;

    private static final int REQUEST_OPEN_BB = 101;
    private static final int REQUEST_OPEN_TEXT = 102;
    private static final int REQUEST_CONVERT_INPUT = 103;
    private static final int REQUEST_SAVE = 104;
    private static final int REQUEST_SAVE_CONVERTED = 105;

    private static final int MIN_SAMPLE_RATE = 8000;
    private static final int MAX_SAMPLE_RATE = 192000;
    private static final int MAX_UNDO_STEPS = 150;
    private static final String PREFS_NAME = "bytebeat_composer_preferences";
    private static final String THEME_KEY = "theme_mode";

    private MainBinding binding;
    private final BytebeatPlayer player = new BytebeatPlayer();
    private final ArrayList<String> bytebeatTypes = new ArrayList<>();
    private final ArrayList<String> logEntries = new ArrayList<>();
    private final ArrayList<String> undoHistory = new ArrayList<>();
    private final ArrayList<String> redoHistory = new ArrayList<>();

    private ArrayAdapter<String> logAdapter;
    private ArrayAdapter<String> typeAdapter;

    private boolean isChanged;
    private boolean suppressHistory;
    private boolean documentWritable;
    private int selectedType;
    private int currentFileType = FORMAT_UNKNOWN;
    private int pendingSaveFormat = FORMAT_UNKNOWN;
    private int savedType;
    private int savedSampleRate = 8000;
    private int editorFontSize = 18;
    private String currentFile = "Untitled";
    private String savedEditorText = "";
    private String lastEditorText = "";
    private String pendingConvertedJson;
    private String pendingConversionName;
    private Runnable actionAfterSave;
    private Uri currentDocumentUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        int nightMode =
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .getInt(THEME_KEY, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(nightMode);
        DynamicColors.applyIfAvailable(this);
        super.onCreate(savedInstanceState);

        binding = MainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState != null) {
            restoreAppState(savedInstanceState);
        }
        initialize();
        initializeLogic(savedInstanceState == null);
        updateTitle();
        applyEditorFontSize();

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_VIEW.equals(action) && type != null) {
            if (type.equals("application/vnd.jarredapps.bb")) {
                final Uri fileUri = intent.getData();
                final int flags =
                        intent.getFlags()
                                & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                withUnsavedChanges(
                        new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    openBytebeatDocument(fileUri, flags);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        });
            }
        }
    }

    private void initialize() {
        setSupportActionBar(binding.Toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setHomeButtonEnabled(false);
        }

        lastEditorText = binding.editor.getText().toString();
        binding.editor.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        String current = s.toString();
                        if (!suppressHistory && !current.equals(lastEditorText)) {
                            undoHistory.add(lastEditorText);
                            if (undoHistory.size() > MAX_UNDO_STEPS) {
                                undoHistory.remove(0);
                            }
                            redoHistory.clear();
                            lastEditorText = current;
                            updateDirtyState();
                            invalidateOptionsMenu();
                        }
                    }

                    @Override
                    public void afterTextChanged(Editable s) {}
                });

        binding.freqInput.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        updateDirtyState();
                    }

                    @Override
                    public void afterTextChanged(Editable s) {}
                });

        binding.Fab.setOnClickListener(
                v -> {
                    boolean showLogs = binding.listlogs.getVisibility() != View.VISIBLE;
                    binding.listlogs.setVisibility(showLogs ? View.VISIBLE : View.GONE);
                    updateLogButton(showLogs);
                });

        binding.play.setOnClickListener(v -> playFormula());
        binding.stop.setOnClickListener(v -> stopPlayback());
        binding.bytebeatTypeSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent, View view, int position, long id) {
                        selectedType = position;
                        updateDirtyState();
                        invalidateOptionsMenu();
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });
    }

    private void initializeLogic(boolean showWelcome) {
        bytebeatTypes.clear();
        bytebeatTypes.add("Bytebeat");
        bytebeatTypes.add("Signed Bytebeat");

        typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, bytebeatTypes);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.bytebeatTypeSpinner.setAdapter(typeAdapter);
        binding.bytebeatTypeSpinner.setSelection(
                Math.max(0, Math.min(selectedType, bytebeatTypes.size() - 1)));

        if (showWelcome || logEntries.isEmpty()) {
            addLog("Welcome to Jarred's Bytebeat Composer");
        }
        int materialListItem =
                getResources()
                        .getIdentifier(
                                "material_list_item_single_line",
                                "layout",
                                "com.google.android.material");
        if (materialListItem == 0) materialListItem = android.R.layout.simple_list_item_1;
        logAdapter = new ArrayAdapter<>(this, materialListItem, logEntries);
        binding.listlogs.setAdapter(logAdapter);
        updateLogButton(binding.listlogs.getVisibility() == View.VISIBLE);
        updateDirtyState();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem undo = menu.findItem(R.id.undo);
        MenuItem redo = menu.findItem(R.id.redo);
        if (undo != null) undo.setEnabled(!undoHistory.isEmpty());
        if (redo != null) redo.setEnabled(!redoHistory.isEmpty());

        int themeMode =
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .getInt(THEME_KEY, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        checkMenuItem(
                menu, R.id.themeSystem, themeMode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        checkMenuItem(menu, R.id.themeLight, themeMode == AppCompatDelegate.MODE_NIGHT_NO);
        checkMenuItem(menu, R.id.themeDark, themeMode == AppCompatDelegate.MODE_NIGHT_YES);
        return super.onPrepareOptionsMenu(menu);
    }

    private void checkMenuItem(Menu menu, int itemId, boolean checked) {
        MenuItem item = menu.findItem(itemId);
        if (item != null) item.setChecked(checked);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.newMenu) {
            _newFile();
        } else if (id == R.id.openBB) {
            _openBBFile();
        } else if (id == R.id.openTxt) {
            _openTxtFile();
        } else if (id == R.id.saveBB) {
            _saveBBFile();
        } else if (id == R.id.saveTxt) {
            _saveTxtFile();
        } else if (id == R.id.convertTxtToBb) {
            convertTxtToBB();
        } else if (id == R.id.undo) {
            undo();
        } else if (id == R.id.redo) {
            redo();
        } else if (id == R.id.zoomIn) {
            changeEditorFontSize(2);
        } else if (id == R.id.zoomOut) {
            changeEditorFontSize(-2);
        } else if (id == R.id.themeSystem) {
            setThemeMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, "System default");
        } else if (id == R.id.themeLight) {
            setThemeMode(AppCompatDelegate.MODE_NIGHT_NO, "Light");
        } else if (id == R.id.themeDark) {
            setThemeMode(AppCompatDelegate.MODE_NIGHT_YES, "Dark");
        } else if (id == R.id.clearLog) {
            logEntries.clear();
            if (logAdapter != null) logAdapter.notifyDataSetChanged();
            addLog("Log cleared");
        } else if (id == R.id.aboutApp) {
            showAboutDialog();
        } else if (id == R.id.exitApp) {
            withUnsavedChanges(this::finish);
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void playFormula() {
        if (player.isRunning()) player.stop();
        String formula = binding.editor.getText().toString().trim();
        if (formula.isEmpty()) {
            binding.editor.setError("Enter a bytebeat formula first");
            showMessage("Formula is empty");
            return;
        }

        int sampleRate = getSampleRateFromInput();
        if (sampleRate < MIN_SAMPLE_RATE || sampleRate > MAX_SAMPLE_RATE) {
            binding.freqInput.setError("Use a sample rate from 8000 to 192000 Hz");
            showMessage("Sample rate must be between 8000 and 192000 Hz");
            return;
        }

        try {
            BytebeatType type =
                    selectedType == 1
                            ? BytebeatType.BYTEBEAT_SIGNED
                            : BytebeatType.BYTEBEAT_UNSIGNED;
            player.start(formula, sampleRate, type);
            addLog("Playing at " + sampleRate + " Hz · " + bytebeatTypes.get(selectedType));
            Snackbar.make(binding.getRoot(), "Playback started", Snackbar.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            binding.editor.setError(e.getMessage());
            showMessage("Could not start playback: " + safeMessage(e));
        }
    }

    private void stopPlayback() {
        if (player.isRunning()) {
            player.stop();
            addLog("Playback stopped");
            Snackbar.make(binding.getRoot(), "Playback stopped", Snackbar.LENGTH_SHORT).show();
        }
    }

    private void undo() {
        if (undoHistory.isEmpty()) return;
        String current = binding.editor.getText().toString();
        redoHistory.add(current);
        String previous = undoHistory.remove(undoHistory.size() - 1);
        applyHistoryText(previous);
    }

    private void redo() {
        if (redoHistory.isEmpty()) return;
        String current = binding.editor.getText().toString();
        undoHistory.add(current);
        String next = redoHistory.remove(redoHistory.size() - 1);
        applyHistoryText(next);
    }

    private void applyHistoryText(String text) {
        suppressHistory = true;
        binding.editor.setText(text);
        suppressHistory = false;
        lastEditorText = text;
        updateDirtyState();
        invalidateOptionsMenu();
    }

    private void replaceEditorText(String text) {
        String value = text == null ? "" : text;
        suppressHistory = true;
        binding.editor.setText(value);
        suppressHistory = false;
        lastEditorText = value;
        undoHistory.clear();
        redoHistory.clear();
        invalidateOptionsMenu();
    }

    private void changeEditorFontSize(int delta) {
        int next = Math.max(12, Math.min(36, editorFontSize + delta));
        if (next == editorFontSize) return;
        editorFontSize = next;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt("editor_font_size", editorFontSize)
                .apply();
        applyEditorFontSize();
        addLog("Editor text size: " + editorFontSize + " sp");
    }

    private void applyEditorFontSize() {
        editorFontSize =
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .getInt("editor_font_size", editorFontSize);
        binding.editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, editorFontSize);
    }

    private void setThemeMode(int mode, String label) {
        int current =
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .getInt(THEME_KEY, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (current == mode) return;
        addLog("Theme changed to " + label);
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt(THEME_KEY, mode).apply();
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    private void showAboutDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("About Jarred's Bytebeat Composer")
                .setMessage(
                        "Compose and play bytebeat formulas on Android.\n\nSample rates: 8000–192000 Hz.")
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void _isLogVisible() {
        updateLogButton(binding.listlogs.getVisibility() == View.VISIBLE);
    }

    private void updateLogButton(boolean logsVisible) {
        binding.Fab.setImageResource(
                logsVisible ? R.drawable.icon_close_round : R.drawable.icon_chatlog_round);
        binding.Fab.setContentDescription(logsVisible ? "Hide log" : "Show log");
    }

    private void _newFile() {
        withUnsavedChanges(this::createNewFile);
    }

    private void createNewFile() {
        if (player.isRunning()) player.stop();
        currentDocumentUri = null;
        documentWritable = false;
        currentFileType = FORMAT_UNKNOWN;
        currentFile = "Untitled";
        selectedType = 0;
        binding.bytebeatTypeSpinner.setSelection(0);
        binding.freqInput.setText("8000");
        replaceEditorText("");
        markClean();
        updateTitle();
        showMessage("New file created");
    }

    private void withUnsavedChanges(Runnable action) {
        updateDirtyState();
        if (!isChanged) {
            action.run();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Save changes?")
                .setMessage("Your formula has unsaved changes.")
                .setPositiveButton(
                        "Save",
                        (dialog, which) -> {
                            actionAfterSave = action;
                            _saveFileDlg();
                        })
                .setNegativeButton("Discard", (dialog, which) -> action.run())
                .setNeutralButton("Cancel", null)
                .show();
    }

    public void _saveFileDlg() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Save formula as")
                .setItems(
                        new String[] {"Bytebeat file (.bb)", "Text file (.txt)"},
                        (dialog, which) -> {
                            if (which == 0) _saveBBFile();
                            else _saveTxtFile();
                        })
                .setNegativeButton("Cancel", (dialog, which) -> actionAfterSave = null)
                .show();
    }

    public void _openBBFile() {
        withUnsavedChanges(() -> launchOpenDocument(REQUEST_OPEN_BB));
    }

    public void _openTxtFile() {
        withUnsavedChanges(() -> launchOpenDocument(REQUEST_OPEN_TEXT));
    }

    public void _saveBBFile() {
        saveInFormat(FORMAT_BB);
    }

    public void _saveTxtFile() {
        saveInFormat(FORMAT_TEXT);
    }

    private void launchOpenDocument(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            showMessage("No file picker is available on this device");
        }
    }

    private void launchCreateDocument(int requestCode, int format, String suggestedName) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(format == FORMAT_BB ? "application/vnd.jarredapps.bb" : "text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, withExtension(suggestedName, format));
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            showMessage("No file picker is available on this device");
            actionAfterSave = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (requestCode == REQUEST_SAVE) actionAfterSave = null;
            return;
        }

        Uri uri = data.getData();
        int flags =
                data.getFlags()
                        & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) {
            // Some document providers do not offer persistent grants.
        }

        try {
            if (requestCode == REQUEST_OPEN_BB) {
                openBytebeatDocument(uri, flags);
            } else if (requestCode == REQUEST_OPEN_TEXT) {
                openTextDocument(uri, flags);
            } else if (requestCode == REQUEST_CONVERT_INPUT) {
                prepareTextConversion(uri);
            } else if (requestCode == REQUEST_SAVE) {
                String content = contentForFormat(pendingSaveFormat);
                writeDocument(uri, content);
                finishSave(uri, pendingSaveFormat);
            } else if (requestCode == REQUEST_SAVE_CONVERTED) {
                writeDocument(uri, pendingConvertedJson);
                pendingConvertedJson = null;
                showMessage("Converted file saved: " + displayName(uri));
            }
        } catch (Exception e) {
            if (requestCode == REQUEST_SAVE) actionAfterSave = null;
            showMessage("File operation failed: " + safeMessage(e));
        }
    }

    private void openBytebeatDocument(Uri uri, int grantFlags) throws IOException, JSONException {
        String raw = readDocument(uri).trim();
        JSONObject json = new JSONObject(raw);
        if (!json.has("formula") || json.isNull("formula")) {
            throw new JSONException("This .bb file does not contain a formula");
        }

        String formula = json.getString("formula");
        int sampleRate = json.optInt("sampleRate", 8000);
        if (sampleRate < MIN_SAMPLE_RATE || sampleRate > MAX_SAMPLE_RATE) sampleRate = 8000;
        int type = json.optInt("bytebeatType", 0) == 1 ? 1 : 0;

        currentDocumentUri = uri;
        documentWritable = (grantFlags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0;
        currentFileType = FORMAT_BB;
        currentFile = displayName(uri);
        selectedType = type;
        binding.bytebeatTypeSpinner.setSelection(type);
        binding.freqInput.setText(String.valueOf(sampleRate));
        replaceEditorText(formula);
        markClean();
        updateTitle();
        showMessage("Opened " + currentFile);
    }

    private void openTextDocument(Uri uri, int grantFlags) throws IOException {
        String formula = readDocument(uri);
        currentDocumentUri = uri;
        documentWritable = (grantFlags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0;
        currentFileType = FORMAT_TEXT;
        currentFile = displayName(uri);
        replaceEditorText(formula);
        markClean();
        updateTitle();
        showMessage("Opened " + currentFile);
    }

    private void saveInFormat(int format) {
        pendingSaveFormat = format;
        if (format == FORMAT_BB
                && (getSampleRateFromInput() < MIN_SAMPLE_RATE
                        || getSampleRateFromInput() > MAX_SAMPLE_RATE)) {
            binding.freqInput.setError("Use a sample rate from 8000 to 192000 Hz");
            showMessage("Set a valid sample rate before saving a .bb file");
            actionAfterSave = null;
            return;
        }
        String content;
        try {
            content = contentForFormat(format);
        } catch (JSONException e) {
            showMessage("Could not prepare the file: " + safeMessage(e));
            actionAfterSave = null;
            return;
        }

        if (currentDocumentUri != null && currentFileType == format && documentWritable) {
            try {
                writeDocument(currentDocumentUri, content);
                finishSave(currentDocumentUri, format);
                return;
            } catch (IOException e) {
                documentWritable = false;
                addLog("Could not update the current document; choosing a new save location");
            }
        }
        launchCreateDocument(REQUEST_SAVE, format, suggestedFileName(format));
    }

    private String contentForFormat(int format) throws JSONException {
        String formula = binding.editor.getText().toString();
        if (format == FORMAT_TEXT) return formula;
        JSONObject json = new JSONObject();
        json.put("formula", formula);
        json.put("sampleRate", getSampleRateFromInput());
        json.put("bytebeatType", selectedType);
        return json.toString(2);
    }

    private void finishSave(Uri uri, int format) {
        currentDocumentUri = uri;
        documentWritable = true;
        currentFileType = format;
        String name = displayName(uri);
        if (name.isEmpty()) name = suggestedFileName(format);
        currentFile = withExtension(name, format);
        markClean();
        updateTitle();
        showMessage("Saved " + currentFile);
        runActionAfterSave();
    }

    private void runActionAfterSave() {
        Runnable action = actionAfterSave;
        actionAfterSave = null;
        if (action != null) action.run();
    }

    private String suggestedFileName(int format) {
        String base =
                currentFile == null || currentFile.trim().isEmpty() ? "Untitled" : currentFile;
        return withExtension(base, format);
    }

    private String withExtension(String name, int format) {
        String extension = format == FORMAT_BB ? ".bb" : ".txt";
        String base = stripKnownExtension(name == null ? "Untitled" : name.trim());
        if (base.isEmpty()) base = "Untitled";
        return base + extension;
    }

    private String stripKnownExtension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".bb")) return name.substring(0, name.length() - 3);
        if (lower.endsWith(".txt")) return name.substring(0, name.length() - 4);
        return name;
    }

    private String displayName(Uri uri) {
        if (uri == null) return "";
        String name = "";
        try (Cursor cursor =
                getContentResolver()
                        .query(
                                uri,
                                new String[] {OpenableColumns.DISPLAY_NAME},
                                null,
                                null,
                                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameColumn >= 0) name = cursor.getString(nameColumn);
            }
        } catch (Exception ignored) {
            // Fall back to the URI path when a provider does not expose a display name.
        }
        if (name.isEmpty()) name = uri.getLastPathSegment();
        if (name == null) return "";
        int colon = name.lastIndexOf(':');
        if (colon >= 0 && colon < name.length() - 1) name = name.substring(colon + 1);
        return name;
    }

    private String readDocument(Uri uri) throws IOException {
        try (InputStream input = getContentResolver().openInputStream(uri);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IOException("The file could not be opened");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            byte[] bytes = output.toByteArray();
            int offset = 0;
            Charset charset = StandardCharsets.UTF_8;
            if (bytes.length >= 2 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xfe) {
                charset = StandardCharsets.UTF_16LE;
                offset = 2;
            } else if (bytes.length >= 2
                    && (bytes[0] & 0xff) == 0xfe
                    && (bytes[1] & 0xff) == 0xff) {
                charset = StandardCharsets.UTF_16BE;
                offset = 2;
            } else if (looksLikeUtf16Le(bytes)) {
                charset = StandardCharsets.UTF_16LE;
            } else if (looksLikeUtf16Be(bytes)) {
                charset = StandardCharsets.UTF_16BE;
            }
            String text = new String(bytes, offset, bytes.length - offset, charset);
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        }
    }

    private boolean looksLikeUtf16Le(byte[] bytes) {
        int samples = Math.min(bytes.length / 2, 200);
        if (samples < 2) return false;
        int zeros = 0;
        for (int i = 1; i < samples * 2; i += 2) if (bytes[i] == 0) zeros++;
        return zeros > samples / 3;
    }

    private boolean looksLikeUtf16Be(byte[] bytes) {
        int samples = Math.min(bytes.length / 2, 200);
        if (samples < 2) return false;
        int zeros = 0;
        for (int i = 0; i < samples * 2; i += 2) if (bytes[i] == 0) zeros++;
        return zeros > samples / 3;
    }

    private void writeDocument(Uri uri, String content) throws IOException {
        if (content == null) throw new IOException("No content is ready to save");
        try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
            if (output == null) throw new IOException("The file could not be opened for writing");
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.flush();
        }
    }

    public void convertTxtToBB() {
        launchOpenDocument(REQUEST_CONVERT_INPUT);
    }

    private void prepareTextConversion(Uri uri) throws IOException {
        String formula = readDocument(uri);
        if (formula.trim().isEmpty()) {
            showMessage("The selected text file is empty");
            return;
        }
        pendingConversionName = stripKnownExtension(displayName(uri));
        showConversionOptions(formula);
    }

    private void showConversionOptions(String formula) {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding =
                (int)
                        TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                20,
                                getResources().getDisplayMetrics());
        fields.setPadding(padding, padding / 2, padding, 0);

        TextView typeLabel = new TextView(this);
        typeLabel.setText("Bytebeat type");
        fields.addView(typeLabel);

        Spinner typeSpinner = new Spinner(this);
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, bytebeatTypes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(adapter);
        fields.addView(typeSpinner);

        TextView sampleRateLabel = new TextView(this);
        sampleRateLabel.setText("Sample rate (8000–192000 Hz)");
        LinearLayout.LayoutParams labelParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = padding / 2;
        fields.addView(sampleRateLabel, labelParams);

        EditText sampleRateInput = new EditText(this);
        sampleRateInput.setSingleLine(true);
        sampleRateInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        sampleRateInput.setText("8000");
        sampleRateInput.setHint("8000");
        fields.addView(sampleRateInput);

        AlertDialog dialog =
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Convert TXT to .bb")
                        .setView(fields)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Choose output", null)
                        .create();
        dialog.setOnShowListener(
                ignored ->
                        dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                                .setOnClickListener(
                                        v -> {
                                            String value =
                                                    sampleRateInput.getText().toString().trim();
                                            int sampleRate;
                                            try {
                                                sampleRate = Integer.parseInt(value);
                                            } catch (NumberFormatException e) {
                                                sampleRateInput.setError("Enter a sample rate");
                                                return;
                                            }
                                            if (sampleRate < MIN_SAMPLE_RATE
                                                    || sampleRate > MAX_SAMPLE_RATE) {
                                                sampleRateInput.setError("Use 8000–192000 Hz");
                                                return;
                                            }

                                            try {
                                                JSONObject json = new JSONObject();
                                                json.put("formula", formula);
                                                json.put("sampleRate", sampleRate);
                                                json.put(
                                                        "bytebeatType",
                                                        typeSpinner.getSelectedItemPosition());
                                                pendingConvertedJson = json.toString(2);
                                                dialog.dismiss();
                                                launchCreateDocument(
                                                        REQUEST_SAVE_CONVERTED,
                                                        FORMAT_BB,
                                                        (pendingConversionName == null
                                                                        ? "Converted"
                                                                        : pendingConversionName)
                                                                + ".bb");
                                            } catch (JSONException e) {
                                                showMessage("Could not prepare the converted file");
                                            }
                                        }));
        dialog.show();
    }

    private void markClean() {
        savedEditorText = binding.editor.getText().toString();
        savedType = selectedType;
        savedSampleRate = getSampleRateFromInput();
        isChanged = false;
        invalidateOptionsMenu();
    }

    private void updateDirtyState() {
        if (binding == null) return;
        boolean textChanged = !binding.editor.getText().toString().equals(savedEditorText);
        boolean metadataChanged =
                currentFileType == FORMAT_BB
                        && (selectedType != savedType
                                || getSampleRateFromInput() != savedSampleRate);
        isChanged = textChanged || metadataChanged;
    }

    private int getSampleRateFromInput() {
        try {
            return Integer.parseInt(binding.freqInput.getText().toString().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private void updateTitle() {
        binding.Toolbar.setTitle("Jarred's Bytebeat Composer");
        binding.Toolbar.setSubtitle(currentFile == null ? "Untitled" : currentFile);
    }

    private void addLog(String message) {
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        logEntries.add(0, time + "  " + message);
        while (logEntries.size() > 200) logEntries.remove(logEntries.size() - 1);
        if (logAdapter != null) logAdapter.notifyDataSetChanged();
    }

    private void showMessage(String message) {
        addLog(message);
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.trim().isEmpty() ? e.getClass().getSimpleName() : message;
    }

    private void restoreAppState(Bundle state) {
        currentFile = state.getString("current_file", "Untitled");
        String uri = state.getString("current_uri");
        currentDocumentUri = uri == null ? null : Uri.parse(uri);
        documentWritable = state.getBoolean("document_writable", false);
        currentFileType = state.getInt("current_file_type", FORMAT_UNKNOWN);
        selectedType = state.getInt("selected_type", 0);
        savedType = state.getInt("saved_type", selectedType);
        savedSampleRate = state.getInt("saved_sample_rate", 8000);
        savedEditorText = state.getString("saved_editor_text", "");
        editorFontSize = state.getInt("editor_font_size", 18);
        ArrayList<String> savedLogs = state.getStringArrayList("log_entries");
        if (savedLogs != null) logEntries.addAll(savedLogs);
        ArrayList<String> savedUndo = state.getStringArrayList("undo_history");
        if (savedUndo != null) undoHistory.addAll(savedUndo);
        ArrayList<String> savedRedo = state.getStringArrayList("redo_history");
        if (savedRedo != null) redoHistory.addAll(savedRedo);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("current_file", currentFile);
        outState.putString(
                "current_uri", currentDocumentUri == null ? null : currentDocumentUri.toString());
        outState.putBoolean("document_writable", documentWritable);
        outState.putInt("current_file_type", currentFileType);
        outState.putInt("selected_type", selectedType);
        outState.putInt("saved_type", savedType);
        outState.putInt("saved_sample_rate", savedSampleRate);
        outState.putString("saved_editor_text", savedEditorText);
        outState.putInt("editor_font_size", editorFontSize);
        outState.putStringArrayList("log_entries", new ArrayList<>(logEntries));
        outState.putStringArrayList("undo_history", new ArrayList<>(undoHistory));
        outState.putStringArrayList("redo_history", new ArrayList<>(redoHistory));
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        suppressHistory = true;
        super.onRestoreInstanceState(state);
        suppressHistory = false;
        lastEditorText = binding.editor.getText().toString();
        if (logAdapter != null) logAdapter.notifyDataSetChanged();
        updateDirtyState();
        updateTitle();
        invalidateOptionsMenu();
    }

    @Override
    protected void onDestroy() {
        if (player.isRunning()) player.stop();
        super.onDestroy();
    }
}
