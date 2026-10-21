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
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);
		binding.Toolbar.setNavigationOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _v) {
				onBackPressed();
			}
		});
		
		binding.Fab.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				_isLogVisible();
			}
		});
	}
	
	private void initializeLogic() {
		// Bytebeat Spinner Lists
		bytebeatListStr.add("Bytebeat");
		bytebeatListStr.add("Signed Bytebeat");
		binding.bytebeatTypeSpinner.setAdapter(new ArrayAdapter<String>(getBaseContext(), android.R.layout.simple_spinner_dropdown_item, bytebeatListStr));
		((ArrayAdapter)binding.bytebeatTypeSpinner.getAdapter()).notifyDataSetChanged();
		// Welcome on logs
		logListStr.add("Welcome to Jarred's Bytebeat Composer!");
		binding.listlogs.setAdapter(new ArrayAdapter<String>(getBaseContext(), android.R.layout.simple_list_item_1, logListStr));
		((BaseAdapter)binding.listlogs.getAdapter()).notifyDataSetChanged();
		_isLogVisible();
	}
	
	public void _isLogVisible() {
		if (binding.listlogs.getVisibility() == View.GONE) {
			// If the log is gone, 
			// the fab will change the icon to the chat icon
			binding.listlogs.setVisibility(View.VISIBLE);
			binding.Fab.setImageResource(R.drawable.icon_chatlog_round);
		}
		else if (binding.listlogs.getVisibility() == View.VISIBLE) {
			// If the log is visible, 
			// the fab will change the icon to the chat icon
			binding.listlogs.setVisibility(View.GONE);
			binding.Fab.setImageResource(R.drawable.icon_close_round);
		}
	}
	
}