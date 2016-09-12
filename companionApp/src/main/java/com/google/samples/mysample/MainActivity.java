package com.google.samples.mysample;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Base64;
import android.support.v7.widget.RecyclerView;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private DatabaseReference mDatabase;
    private LinearLayoutManager mLayoutManager;
    private FirebaseRecyclerAdapter<DoorbellEntry, DoorbellEntryViewHolder> mAdapter;
    private RecyclerView mDoorbellView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mDatabase = FirebaseDatabase.getInstance().getReference().child("logs");
        mDoorbellView = (RecyclerView) findViewById(R.id.doorbellView);
        mLayoutManager = new LinearLayoutManager(this);
        mLayoutManager.setReverseLayout(true);
        mDoorbellView.setLayoutManager(mLayoutManager);
    }

    @Override
    public void onStart() {
        super.onStart();
        attachAdapter();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAdapter!= null) {
            mAdapter.cleanup();
        }
    }

    void attachAdapter() {
        mAdapter = new FirebaseRecyclerAdapter<DoorbellEntry, DoorbellEntryViewHolder>(DoorbellEntry.class, R.layout.doorbell_entry, DoorbellEntryViewHolder.class, mDatabase) {
            @Override
            protected void populateViewHolder(DoorbellEntryViewHolder viewHolder, DoorbellEntry entry, int position) {
                CharSequence prettyTime = DateUtils.getRelativeDateTimeString(getApplicationContext(), entry.getTimestamp(), DateUtils.SECOND_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0);
                viewHolder.textView1.setText(prettyTime);
                Bitmap bmp = null;
                if (entry.getImage() != null) {
                    byte[] imageBytes = Base64.decode(entry.getImage(), Base64.NO_WRAP);
                    bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                }
                if (bmp != null) {
                    viewHolder.imageView1.setImageBitmap(bmp);
                } else {
                    viewHolder.imageView1.setImageDrawable(getDrawable(R.drawable.ic_image));
                }

                if (entry.getAnnotations() != null) {
                    ArrayList<String> keywords = new ArrayList<String>(entry.getAnnotations().keySet());
                    int limit = 3;
                    if (keywords.size() < limit) {
                        limit = keywords.size();
                    }
                    viewHolder.textView2.setText(TextUtils.join("\n", keywords.subList(0, limit)));
                } else {
                    viewHolder.textView2.setText("no annotations yet");
                }
            }
        };
        mAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                mLayoutManager.smoothScrollToPosition(mDoorbellView, null, mAdapter.getItemCount());
            }
        });
        mDoorbellView.setAdapter(mAdapter);
    }
}
