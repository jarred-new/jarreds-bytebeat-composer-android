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
import android.widget.ListView;
import android.widget.Spinner;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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

        binding.Fab.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View _view) {
                        fabClicks++;
                        if (fabClicks == 1) {
                            binding.listlogs.setVisibility(View.VISIBLE);
                            if (binding.listlogs.getVisibility() == View.VISIBLE) {
                                binding.listlogs.setVisibility(View.GONE);
                                fabClicks = 0;
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
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
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
        // Bytebeat Spinner Lists
        bytebeatListStr.add("Bytebeat");
        bytebeatListStr.add("Signed Bytebeat");
        binding.bytebeatTypeSpinner.setAdapter(
                new ArrayAdapter<String>(
                        getBaseContext(),
                        android.R.layout.simple_spinner_dropdown_item,
                        bytebeatListStr));
        ((ArrayAdapter) binding.bytebeatTypeSpinner.getAdapter()).notifyDataSetChanged();
        // Welcome on logs
        logListStr.add("Welcome to Jarred's Bytebeat Composer!");
        binding.listlogs.setAdapter(
                new ArrayAdapter<String>(
                        getBaseContext(), android.R.layout.simple_list_item_1, logListStr));
        ((BaseAdapter) binding.listlogs.getAdapter()).notifyDataSetChanged();
        _isLogVisible();
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
}
