package com.jarredapps.bytebeatcomposer;

import android.animation.*;
import android.app.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.net.*;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.util.*;
import android.view.*;
import android.view.View;
import android.view.View.*;
import android.view.animation.*;
import android.webkit.*;
import android.widget.*;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.jarredapps.bytebeatcomposer.databinding.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.ArrayList;
import java.util.regex.*;
import org.json.*;

public class MainActivity extends AppCompatActivity {

    private MainBinding binding;

    private int fabClicks = 0;
    private int typePos = 0;
    private BytebeatPlayer player = new BytebeatPlayer();

    private ArrayList<String> bytebeatListStr = new ArrayList<>();
    private ArrayList<String> logListStr = new ArrayList<>();

    private ArrayAdapter<String> listAdapter;
    private ArrayAdapter<String> spinnerAdapter;

    // for file
    private boolean isChanged = false;
    private String currentFile = "Untitled";

    @Override
    protected void onCreate(Bundle _savedInstanceState) {
        DynamicColors.applyIfAvailable(this);
        super.onCreate(_savedInstanceState);
        binding = MainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initialize(_savedInstanceState);
        initializeLogic();
    }

    private void initialize(Bundle _savedInstanceState) {
        setSupportActionBar(binding.Toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        getSupportActionBar().setHomeButtonEnabled(false);
        //		binding.Toolbar.setNavigationOnClickListener(new View.OnClickListener() {
        //			@Override
        //			public void onClick(View _v) {
        //				onBackPressed();
        //			}
        //		});
        
        binding.editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                isChanged = true;
            }
        
            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.Fab.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View _view) {
                        fabClicks++;
                        if (fabClicks == 1) {
                            if (binding.listlogs.getVisibility() == View.VISIBLE) {
                                binding.listlogs.setVisibility(View.GONE);
                                fabClicks = 0;
                            } else {
                                binding.listlogs.setVisibility(View.VISIBLE);
                            }
                        } else if (fabClicks == 2) {
                            binding.listlogs.setVisibility(View.GONE);
                            fabClicks = 0;
                        }
                        _isLogVisible();
                    }
                });

        binding.play.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View _view) {
                        if (player.isRunning()) {
                            player.stop();
                        }

                        String formula = binding.editor.getText().toString().trim();

                        String frequency = binding.freqInput.getText().toString().trim();

                        if (formula.isEmpty()) {
                            binding.editor.setError("Formula is Empty!");
                            //                            logList.add("Formula is Empty!");
                            //                            refreshLog(logList);
                            return;
                        }

                        if (frequency.isEmpty()) {
                            binding.freqInput.setError("You must enter a frequency");
                            //                            logList.add("You must enter a frequency");
                            //                            refreshLog(logList);
                            return;
                        }

                        try {
                            int freqInt = Integer.parseInt(frequency);

                            if (typePos == 0) {
                                player.start(formula, freqInt, BytebeatType.BYTEBEAT_UNSIGNED);

                                //                                logList.add("Playing: " +
                                // formula);
                                //                                refreshLog(logList);
                            } else if (typePos == 1) {
                                player.start(formula, freqInt, BytebeatType.BYTEBEAT_SIGNED);

                                //                                logList.add("Playing: " +
                                // formula);
                                //                                refreshLog(logList);
                            }
                        } catch (NumberFormatException e) {
                            binding.freqInput.setError("Frequency must be numeric");

                            //                            logList.add("Frequency must be numeric");
                            //                            refreshLog(logList);
                            return;

                        } catch (RuntimeException e) {
                            e.printStackTrace();
                            binding.editor.setError(
                                    "java.lang.RuntimeException Error: " + e.toString());

                            //                            logList.add("java.lang.RuntimeException
                            // Error: "
                            //                                        + e.toString());
                            //                            refreshLog(logList);
                            return;
                        } catch (Exception e) {
                            e.printStackTrace();
                            binding.editor.setError("java.lang.Exception Error: " + e.toString());

                            //                            logList.add("java.lang.RuntimeException
                            // Error: "
                            //                                        + e.toString());
                            //                            refreshLog(logList);
                            return;
                        }
                    }
                });

        binding.stop.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View _view) {
                        if (player.isRunning()) {
                            player.stop();
                        }
                    }
                });

        binding.bytebeatTypeSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent, View view, int position, long id) {
                        typePos = position;
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        // This is called when the selection disappears from the view,
                        // which rarely happens but must be implemented.
                    }
                });
    }

    private void initializeLogic() {
        // ArrayAdapter and Layout from Material 3
        /* For ListView */
        int listML =
                getResources()
                        .getIdentifier(
                                "material_list_item_single_line",
                                "layout",
                                "com.google.android.material");
        if (listML == 0) {
            // Graceful fallback to default Android layout if something goes wrong
            listML = android.R.layout.simple_list_item_1;
        }
        listAdapter = new ArrayAdapter<>(this, listML, logListStr);

        /* For Spinner */
        int spinnerML =
                getResources()
                        .getIdentifier(
                                "material_spinner_item", "layout", "com.google.android.material");
        int dropdownML = // dropdown
                getResources()
                        .getIdentifier(
                                "material_spinner_dropdown_item",
                                "layout",
                                "com.google.android.material");
        if (spinnerML == 0) {
            // Graceful fallback to default Android layout if something goes wrong
            spinnerML = android.R.layout.simple_spinner_item;
        }
        if (dropdownML == 0) {
            // Graceful fallback to default Android layout if something goes wrong
            dropdownML = android.R.layout.simple_spinner_dropdown_item;
        }
        spinnerAdapter = new ArrayAdapter<>(this, spinnerML, bytebeatListStr);
        spinnerAdapter.setDropDownViewResource(dropdownML);

        // Bytebeat Spinner Lists
        bytebeatListStr.add("Bytebeat");
        bytebeatListStr.add("Signed Bytebeat");
        if (spinnerAdapter != null) {
            binding.bytebeatTypeSpinner.setAdapter(spinnerAdapter);
        } else {
            binding.bytebeatTypeSpinner.setAdapter(
                    new ArrayAdapter<String>(
                            getBaseContext(),
                            android.R.layout.simple_spinner_item,
                            bytebeatListStr));
        }
        ((ArrayAdapter) binding.bytebeatTypeSpinner.getAdapter()).notifyDataSetChanged();

        // Welcome on logs
        logListStr.add("Welcome to Jarred's Bytebeat Composer!");
        if (listAdapter != null) {
            binding.listlogs.setAdapter(listAdapter);
        } else {
            binding.listlogs.setAdapter(
                    new ArrayAdapter<String>(
                            getBaseContext(), android.R.layout.simple_list_item_1, logListStr));
        }
        ((BaseAdapter) binding.listlogs.getAdapter()).notifyDataSetChanged();
        _isLogVisible();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return super.onCreateOptionsMenu(menu);
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
        } /* else if (id == R.id.exportMenu) {

        } else if (id == R.id.clearLog) {

        } else if (id == R.id.aboutApp) {

        } else if (id == R.id.exitApp) {

        } */
        return super.onOptionsItemSelected(item);
    }

    public void _isLogVisible() {
        if (binding.listlogs.getVisibility() == View.GONE) {
            // the fab will change the icon to the chat icon
            binding.Fab.setImageResource(R.drawable.icon_chatlog_round);
        } else if (binding.listlogs.getVisibility() == View.VISIBLE) {
            // the fab will change the icon to the chat icon
            binding.Fab.setImageResource(R.drawable.icon_close_round);
        }
    }

    public void _newFile() {
        if (isChanged) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Do you want to save?")
                    .setPositiveButton(
                            android.R.string.yes,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dia, int which) {
                                    _saveFileDlg();
                                }
                            })
                    .setNegativeButton(
                            android.R.string.no,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dia, int which) {
                                    isChanged = false;
                                    _newFile();
                                }
                            }).show();
        } else {
            currentFile = "Untitled";
            binding.editor.setText("");
        }
    }

    public void _saveFileDlg() {
        new MaterialAlertDialogBuilder(this)
                    .setTitle("What kind of file type to save?")
                    .setPositiveButton(
                            "Save as .bb",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dia, int which) {
                                    _saveBBFile();
                                }
                            })
                    .setNegativeButton(
                            "Save as .txt",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dia, int which) {
                                    _saveTxtFile();
                                }
                            }).show();
    }

    // Open bb and txt
    public void _openBBFile() {
        // TODO: Implement this method

    }
    
    public void _openTxtFile() {
        // TODO: Implement this method

    }

    // Save bb and txt
    public void _saveBBFile() {
        // TODO: Implement this method

    }
    
    public void _saveTxtFile() {
        // TODO: Implement this method

    }
}
