package com.techyourchance.multithreading.exercises.exercise3;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.techyourchance.multithreading.R;
import com.techyourchance.multithreading.common.BaseFragment;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Exercise3Fragment extends BaseFragment {

    private static final int SECONDS_TO_COUNT = 3;

    private final String TAG = "Exercise3";

    public static Fragment newInstance() {
        return new Exercise3Fragment();
    }

    private Button mBtnCountSeconds;
    private TextView mTxtCount;

    private final Handler mUiHandler = new Handler(Looper.getMainLooper());
    private WorkerHandler mWorkerHandler;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_exercise_3, container, false);

        mBtnCountSeconds = view.findViewById(R.id.btn_count_seconds);
        mTxtCount = view.findViewById(R.id.txt_count);

        mBtnCountSeconds.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                countIterations();
            }
        });

        return view;
    }

    @Override
    protected String getScreenTitle() {
        return "Exercise 3";
    }

    @Override
    public void onStart() {
        super.onStart();
        mWorkerHandler = new WorkerHandler();
    }

    @Override
    public void onStop() {
        super.onStop();
        mWorkerHandler.stop();
    }

    private void countIterations() {
        /*
        1. Disable button to prevent multiple clicks
        2. Start counting on background thread using loop and Thread.sleep()
        3. Show count in TextView
        4. When count completes, show "done" in TextView and enable the button
         */

        mUiHandler.post(() -> {
            Log.d(TAG, "Disabling button in " + Thread.currentThread().getName());
            mBtnCountSeconds.setEnabled(false);
        });
        mWorkerHandler.post(() -> {
            for (int ctr = 0; ctr < SECONDS_TO_COUNT; ctr++) {
                try {
                    Log.d(TAG, "Doing background count with current value " + ctr + " in " + Thread.currentThread().getName());
                    final int currentCount = ctr + 1;
                    mUiHandler.post(() -> {
                        Log.d(TAG, "Setting text to \"" + currentCount + "\" in "  + Thread.currentThread().getName());
                        mTxtCount.setText(String.valueOf(currentCount));
                    });
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
            }

            mUiHandler.post(() -> {
                Log.d(TAG, "Enabling button and setting text to \"DONE\" in " + Thread.currentThread().getName());
                mBtnCountSeconds.setEnabled(true);
                mTxtCount.setText("DONE");
            });
        });


    }

    private class WorkerHandler {
        private final BlockingQueue<Runnable> mQueue = new LinkedBlockingQueue<>();

        private final Runnable POISON = () -> {
        };

        public WorkerHandler() {
            initWorkerThread();
        }

        private void initWorkerThread() {
            new Thread(() -> {
                Log.d("CustomHandler", "worker (looper) thread initialized");
                while (true) {
                    Runnable runnable;
                    try {
                        runnable = mQueue.take();
                    } catch (InterruptedException e) {
                        return;
                    }
                    if (runnable == POISON) {
                        Log.d("CustomHandler", "poison data detected; stopping working thread");
                        return;
                    }
                    runnable.run();
                }
            }).start();
        }

        public void stop() {
            mQueue.clear();
            mQueue.add(POISON);
        }

        public void post(Runnable job) {
            mQueue.add(job);
        }
    }
}
