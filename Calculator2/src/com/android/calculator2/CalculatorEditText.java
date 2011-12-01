/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.calculator2;

import android.text.ClipboardManager;
import android.content.Context;
import android.content.res.Resources;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.Toast;
import android.view.View;
import android.widget.TextView;

// Java Packages
import java.lang.Class;
import java.lang.NoSuchMethodException;
import java.lang.reflect.Method;
import java.lang.IllegalAccessException;
import java.lang.reflect.InvocationTargetException;
import java.lang.IllegalArgumentException;

import com.tombarrasso.android.calculator2.R;

public class CalculatorEditText extends EditText {

    private static final String LOG_TAG = "Calculator2";
    private static final int CUT = 0;
    private static final int COPY = 1;
    private static final int PASTE = 2;
    private String[] mMenuItemsStrings;
    
    private static final Class<TextView> mViewClass = TextView.class;
    private static Method mMethod;
    
    public static final boolean setCustomSelectionActionModeCallback(
    	View mView, ActionMode.Callback mCallback)
    {
    	// Cache the Method for performance.
		if (mMethod == null)
		{
			try
			{
				// Check to see if an overscroll method exists.
				mMethod = mViewClass.getMethod("setCustomSelectionActionModeCallback",
					new Class[] { ActionMode.Callback.class });
			}
			catch(NoSuchMethodException e)
			{
				return false;
			}
		}
		
		// Call the method if it exists.
		// It is bad practice to catch all exceptions
		// but Reflection has so many, all with the
		// same meaning that no method was called.
		try
		{
			mMethod.invoke(mView,
				new Object[] { mCallback });
		}
		catch(Exception e)
		{
			return false;
		}

		return true;
    }

    public CalculatorEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        CalculatorEditText.setCustomSelectionActionModeCallback(this, new NoTextSelectionMode());
        setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
       if ((event.getAction() & MotionEvent.ACTION_MASK) == MotionEvent.ACTION_UP) {
            // Hack to prevent keyboard and insertion handle from showing.
           cancelLongPress();
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performLongClick() {
        showContextMenu();
        return true;
    }

    private class MenuHandler implements MenuItem.OnMenuItemClickListener {
        public boolean onMenuItemClick(MenuItem item) {
            return onTextContextMenuItem(item.getTitle());
        }
    }

    public boolean onTextContextMenuItem(CharSequence title) {
        boolean handled = false;
        if (TextUtils.equals(title, mMenuItemsStrings[CUT])) {
            cutContent();
            handled = true;
        } else if (TextUtils.equals(title,  mMenuItemsStrings[COPY])) {
            copyContent();
            handled = true;
        } else if (TextUtils.equals(title,  mMenuItemsStrings[PASTE])) {
            pasteContent();
            handled = true;
        }
        return handled;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu) {
        MenuHandler handler = new MenuHandler();
        if (mMenuItemsStrings == null) {
            Resources resources = getResources();
            mMenuItemsStrings = new String[3];
            mMenuItemsStrings[CUT] = resources.getString(android.R.string.cut);
            mMenuItemsStrings[COPY] = resources.getString(android.R.string.copy);
            mMenuItemsStrings[PASTE] = resources.getString(android.R.string.paste);
        }
        for (int i = 0; i < mMenuItemsStrings.length; i++) {
            menu.add(Menu.NONE, i, i, mMenuItemsStrings[i]).setOnMenuItemClickListener(handler);
        }
        if (getText().length() == 0) {
            menu.getItem(CUT).setVisible(false);
            menu.getItem(COPY).setVisible(false);
        }
        CharSequence primaryClip = getClipText();
        if (primaryClip == null || !canPaste(primaryClip)) {
            menu.getItem(PASTE).setVisible(false);
        }
    }

    private void setClipText(CharSequence clip) {
        ClipboardManager clipboard = (ClipboardManager) getContext().
                getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setText(clip);
    }

    private void copyContent() {
        final Editable text = getText();
        int textLength = text.length();
        setSelection(0, textLength);
        ClipboardManager clipboard = (ClipboardManager)
        	getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setText(text);
        Toast.makeText(getContext(),
        	R.string.text_copied_toast, Toast.LENGTH_SHORT).show();
        setSelection(textLength);
    }

    private void cutContent() {
        final Editable text = getText();
        int textLength = text.length();
        setSelection(0, textLength);
        setClipText(text);
        ((Editable) getText()).delete(0, textLength);
        setSelection(0);
    }

    private CharSequence getClipText() {
        ClipboardManager clipboard = (ClipboardManager)
        	getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        return clipboard.getText();
    }

    private void pasteContent() {
        CharSequence clip = getClipText();
        if (clip != null) {
			if (canPaste(clip)) {
				((Editable) getText()).insert(getSelectionEnd(), clip);
			}
		}
    }

    private boolean canPaste(CharSequence paste) {
        boolean canPaste = true;
        try {
            Float.parseFloat(paste.toString());
        } catch (NumberFormatException e) {
            Log.e(LOG_TAG, "Error turning string to integer. Ignoring paste.", e);
            canPaste = false;
        }
        return canPaste;
    }

    class NoTextSelectionMode implements ActionMode.Callback
    {
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return false;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            copyContent();
            // Prevents the selection action mode on double tap.
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {}

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }
    }
}
