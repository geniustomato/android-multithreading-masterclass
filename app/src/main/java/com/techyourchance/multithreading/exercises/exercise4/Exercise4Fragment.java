package com.techyourchance.multithreading.exercises.exercise4;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.techyourchance.multithreading.DefaultConfiguration;
import com.techyourchance.multithreading.R;
import com.techyourchance.multithreading.common.BaseFragment;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.Fragment;

public class Exercise4Fragment extends BaseFragment {

    public static Fragment newInstance() {
        return new Exercise4Fragment();
    }

    private static int MAX_TIMEOUT_MS = DefaultConfiguration.DEFAULT_FACTORIAL_TIMEOUT_MS;

    private Handler mUiHandler = new Handler(Looper.getMainLooper());

    private EditText mEdtArgument;
    private EditText mEdtTimeout;
    private Button mBtnStartWork;
    private TextView mTxtResult;

    private volatile int mNumberOfThreads; // TODO I can't seem to remember what Vasily said would be the best thing to consider before resorting to volatile
    private volatile ComputationRange[] mThreadsComputationRanges;
    private BigInteger[] mThreadsComputationResults;
    private final AtomicInteger mNumOfFinishedThreads = new AtomicInteger(0);

    private volatile AtomicLong mComputationTimeoutTime;

    private final AtomicBoolean mAbortComputation = new AtomicBoolean(false);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_exercise_4, container, false);

        mEdtArgument = view.findViewById(R.id.edt_argument);
        mEdtTimeout = view.findViewById(R.id.edt_timeout);
        mBtnStartWork = view.findViewById(R.id.btn_compute);
        mTxtResult = view.findViewById(R.id.txt_result);

        mBtnStartWork.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mEdtArgument.getText().toString().isEmpty()) {
                    return;
                }

                mTxtResult.setText("");
                mBtnStartWork.setEnabled(false);


                InputMethodManager imm =
                        (InputMethodManager) requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(mBtnStartWork.getWindowToken(), 0);

                int argument = Integer.valueOf(mEdtArgument.getText().toString());

                computeFactorial(argument, getTimeout());
            }
        });

        return view;
    }

    @Override
    public void onStop() {
        super.onStop();
        mAbortComputation.set(true);
    }

    @Override
    protected String getScreenTitle() {
        return "Exercise 4";
    }

    private int getTimeout() {
        int timeout;
        if (mEdtTimeout.getText().toString().isEmpty()) {
            timeout = MAX_TIMEOUT_MS;
        } else {
            timeout = Integer.valueOf(mEdtTimeout.getText().toString());
            if (timeout > MAX_TIMEOUT_MS) {
                timeout = MAX_TIMEOUT_MS;
            }
        }
        return timeout;
    }

    private void computeFactorial(final int factorialArgument, final int timeout) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                initComputationParams(factorialArgument, timeout); // Worker thread -- the initial one // Will this finish first before startComputation()?
                startComputation(); // 1 or more worker threads // Multithreading happens here so this is where I have to keep an extra eye on
                waitForThreadsResultsOrTimeoutOrAbort(); // 1 worker thread // Though this appears independent, this thread will run at the same time as computation threads
                processComputationResults(); // Worker to UI Thread
            }
        }).start();
    }

    private void initComputationParams(int factorialArgument, int timeout) {
        Log.d("Exercise4", "In initComputationParams()");

        mNumberOfThreads = factorialArgument < 20
                ? 1 : Runtime.getRuntime().availableProcessors();

        mNumOfFinishedThreads.set(0);

        mAbortComputation.set(false);

        mThreadsComputationResults = new BigInteger[mNumberOfThreads];

        mThreadsComputationRanges = new ComputationRange[mNumberOfThreads];

        initThreadsComputationRanges(factorialArgument);

        mComputationTimeoutTime =  new AtomicLong(System.currentTimeMillis() + timeout);

        Log.d("Exercise4", "Ending initComputationParams()");
    }

    private void initThreadsComputationRanges(int factorialArgument) {
        int computationRangeSize = factorialArgument / mNumberOfThreads;

        long nextComputationRangeEnd = factorialArgument;
        for (int i = mNumberOfThreads - 1; i >= 0; i--) {

            if(i == 0) {
                mThreadsComputationRanges[i] = new ComputationRange(
                        1,
                        nextComputationRangeEnd
                );
            } else {
                mThreadsComputationRanges[i] = new ComputationRange(
                        nextComputationRangeEnd - computationRangeSize + 1,
                        nextComputationRangeEnd
                );
            }

            nextComputationRangeEnd = mThreadsComputationRanges[i].start - 1; // READ
        }

        // add potentially "remaining" values to first thread's range
        // mThreadsComputationRanges[0].start = 1; // I'm not so sure what this WRITE operation does...the weird thing is this always runs no matter what
        // TODO A bit sus because the first range will seem to always cover the entire scope of all factorials? so given factorial of 12 and 3 threads, range would be 1-12 (initially 9-12), 1-4, 5-8
    }

    @WorkerThread
    private void startComputation() {
        for (int i = 0; i < mNumberOfThreads; i++) {

            final int threadIndex = i;

            new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.d("Exercise4", "Starting thread " + (threadIndex + 1) + " out of " + mNumberOfThreads);
                    Log.d("Exercise4", "with range start|end: " + mThreadsComputationRanges[threadIndex].start + "|" + mThreadsComputationRanges[threadIndex].end);

                    long rangeStart = mThreadsComputationRanges[threadIndex].start; // READ
                    long rangeEnd = mThreadsComputationRanges[threadIndex].end; // READ
                    BigInteger product = new BigInteger("1");
                    for (long num = rangeStart; num <= rangeEnd; num++) {
                        if (isTimedOut()) {
                            break;
                        }
                        product = product.multiply(new BigInteger(String.valueOf(num)));
                    }
                    mThreadsComputationResults[threadIndex] = product;
                    mNumOfFinishedThreads.incrementAndGet();
                    Log.d("Exercise4", "Ending thread " + (threadIndex + 1) + " out of " + mNumberOfThreads);
                }
            }).start();

        }
    }

    @WorkerThread
    private void waitForThreadsResultsOrTimeoutOrAbort() {
        while (true) {
            Log.d("Exercise4", "Started waitForThreadsResults()");
            if (mNumOfFinishedThreads.get() == mNumberOfThreads) {
                Log.d("Exercise4", "Ending waitForThreadsResults() by finish");
                break;
            } else if(mAbortComputation.get()) {
                Log.d("Exercise4", "Ending waitForThreadsResults() by abort");
                break;
            } else if (isTimedOut()) {
                Log.d("Exercise4", "Ending waitForThreadsResults() by time out");
                break;
            } else {
                try {
                    Log.d("Exercise4", "Retrying waitForThreadsResults()");
                    Thread.sleep(100); // TODO Will this lead to an issue with keeping the lock?
                } catch (InterruptedException e) {
                    // do nothing and keep looping
                }
            }
        }
    }

    @WorkerThread
    private void processComputationResults() {
        Log.d("Exercise4", "Started processComputationResults()");
        String resultString;

        if (mAbortComputation.get()) {
            Log.d("Exercise4", "Result of processComputationResults() - aborted");
            resultString = "Computation aborted";
        }
        else {
            Log.d("Exercise4", "Result of processComputationResults() - completed");
            resultString = computeFinalResult().toString();
        }

        // need to check for timeout after computation of the final result
        if (isTimedOut()) {
            Log.d("Exercise4", "Result of processComputationResults() - timed out");
            resultString = "Computation timed out";
        }

        final String finalResultString = resultString;

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                Log.d("Exercise4", "Attempting to display processComputationResults() result " + finalResultString);
                if (!Exercise4Fragment.this.isStateSaved()) {
                    Log.d("Exercise4", "Updating of UI in progress");
                    mTxtResult.setText(finalResultString);
                    mBtnStartWork.setEnabled(true);
                }
            }
        });
        Log.d("Exercise4", "Ending with computation range: " + Arrays.toString(mThreadsComputationRanges) + "\n\ncomputation results: " + Arrays.toString(mThreadsComputationResults));
    }

    @WorkerThread
    private BigInteger computeFinalResult() {
        BigInteger result = new BigInteger("1");
        for (int i = 0; i < mNumberOfThreads; i++) {
            if (isTimedOut()) {
                break;
            }
            result = result.multiply(mThreadsComputationResults[i]);
        }
        return result;
    }

    private boolean isTimedOut() {
        return System.currentTimeMillis() >= mComputationTimeoutTime.get();
    }

    private static class ComputationRange {
        private final long start;
        private final long end;

        public ComputationRange(long start, long end) {
            this.start = start;
            this.end = end;
        }

        @NonNull
        @Override
        public String toString() {
            return start + "|" + end;
        }
    }
}
