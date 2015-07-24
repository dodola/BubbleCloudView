/*
 * Copyright (c) 2010, Sony Ericsson Mobile Communication AB. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, 
 * are permitted provided that the following conditions are met:
 *
 *    * Redistributions of source code must retain the above copyright notice, this 
 *      list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright notice,
 *      this list of conditions and the following disclaimer in the documentation
 *      and/or other materials provided with the distribution.
 *    * Neither the name of the Sony Ericsson Mobile Communication AB nor the names
 *      of its contributors may be used to endorse or promote products derived from
 *      this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.dodola.bubblecloudexample;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.dodola.bubblecloud.BubbleCloudView;
import com.dodola.bubblecloud.utils.FileManagerImageLoader;
import com.pkmmte.view.CircularImageView;

import java.util.ArrayList;
import java.util.List;


public class TestActivity extends Activity {

    private BubbleCloudView mListView;


    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        FileManagerImageLoader.prepare(this.getApplication());

        final ArrayList<String> contacts = createContactList(19);
        final MyAdapter adapter = new MyAdapter(this, contacts);

        mListView = (BubbleCloudView) findViewById(R.id.my_list);
        mListView.setAdapter(adapter);

        mListView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(final AdapterView<?> parent, final View view,
                                    final int position, final long id) {
                final String message = "OnClick: " + contacts.get(position);
                Toast.makeText(TestActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });


    }

    private ArrayList<String> createContactList(final int size) {
        final ArrayList<String> contacts = new ArrayList<>();
        List<PackageInfo> packages = getPackageManager().getInstalledPackages(0);
        for (int i = 0; i < size; i++) {
            final PackageInfo packageInfo = packages.get(i);
            contacts.add(packageInfo.applicationInfo.sourceDir);
        }
        return contacts;
    }

    private static class MyAdapter extends ArrayAdapter<String> {

        public MyAdapter(final Context context, final ArrayList<String> contacts) {
            super(context, 0, contacts);
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(getContext()).inflate(R.layout.list_item, null);
            }
            final CircularImageView itemRound = (CircularImageView) view.findViewById(R.id.item_round);
            FileManagerImageLoader.getInstance().addTask(getItem(position), itemRound, null, 48, 48, false);
            return view;
        }
    }
}
