package com.dodola.bubblecloud.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import android.annotation.TargetApi;
import android.app.Application;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore.Files.FileColumns;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Video;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.View;
import android.widget.ImageView;

import com.dodola.bubblecloud.bitmapfun.AsyncTask;
import com.dodola.bubblecloud.bitmapfun.ImageCache;
import com.dodola.bubblecloud.bitmapfun.ImageFetcher;
import com.dodola.bubblecloud.bitmapfun.ImageResizer;
import com.dodola.bubblecloud.bitmapfun.RecyclingBitmapDrawable;

public class FileManagerImageLoader {

    private SparseArray<SoftReference<Bitmap>> defaultBitmap = new SparseArray<SoftReference<Bitmap>>();
    public static final Executor DUAL_THREAD_EXECUTOR = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    public boolean isExitApp = true; // 是否退出app
    private static final int MINI_KIND = 1;
    // private static final int FULL_SCREEN_KIND = 2;
    // private static final int MICRO_KIND = 3;
    private final Object mPauseWorkLock = new Object();
    protected boolean mPauseWork = false;
    private boolean mExitTasksEarly = false;
    protected Resources mResources;
    private Context mContext;
    protected static ImageCache mImageCache;
    private static FileManagerImageLoader instance;

    public synchronized static FileManagerImageLoader getInstance() {
        return instance;
    }

    public static synchronized void prepare(Application appContext) {
        if (instance == null) {
            instance = new FileManagerImageLoader(appContext);
            ImageCache.ImageCacheParams cacheParams =
                    new ImageCache.ImageCacheParams(appContext, Environment.getExternalStorageDirectory().getAbsolutePath());
            cacheParams.setMemCacheSizePercent(0.25f);
            mImageCache = ImageCache.getInstance(cacheParams);
        }
    }

    private FileManagerImageLoader(Context context) {
        mResources = context.getResources();
        mContext = context;
    }

    /**
     * 启动下载调度程序
     */
    public void startDownLoadThread() {
        isExitApp = false;
    }

    /**
     * 停止所有任务调�?
     */
    public void endDownLoadThread() {
        try {
            // mImageCache.clearMemoryCache();
            ThreadPoolExecutor ex = (ThreadPoolExecutor) DUAL_THREAD_EXECUTOR;
            ex.getQueue().clear();
        } catch (Exception ex) {

        }
    }

    public void setExitTasksEarly(boolean exitTasksEarly) {
        mExitTasksEarly = exitTasksEarly;
        setPauseWork(false);
    }

    public void setPauseWork(boolean pauseWork) {
        synchronized (mPauseWorkLock) {
            mPauseWork = pauseWork;
            if (!mPauseWork) {
                mPauseWorkLock.notifyAll();
            }
        }
    }

    private class BitmapWorkerTask extends AsyncTask<Object, Void, BitmapDrawable> {
        private Object data;
        private boolean isBig;
        private int mWidth, mHeight;
        private final WeakReference<ImageView> imageViewReference;
        public final WeakReference<View> frameViewReference;

        public BitmapWorkerTask(ImageView imageView) {
            imageViewReference = new WeakReference<ImageView>(imageView);
            frameViewReference = null;
        }

        public BitmapWorkerTask(ImageView imageView, View frameView) {
            imageViewReference = new WeakReference<ImageView>(imageView);
            frameViewReference = new WeakReference<View>(frameView);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

        }

        /**
         * Background processing.
         */
        @Override
        protected BitmapDrawable doInBackground(Object... params) {

            synchronized (mPauseWorkLock) {
                while (mPauseWork && !isCancelled()) {
                    try {
                        mPauseWorkLock.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
            String info = (String) params[0];

            RecyclingBitmapDrawable drawable = null;

            if (info != null) {

                if (!isCancelled() && getAttachedImageView() != null && !mExitTasksEarly) {
                    try {
                        drawable = getIconDrawable(info);
                    } catch (Throwable ex) {
                        drawable = null;
                    }
                }

            }

            if (drawable != null && !mPauseWork) {
                mImageCache.addBitmapToCache(info, drawable);
            }

            return drawable;
        }

        @Override
        protected void onPostExecute(BitmapDrawable value) {
            if (isCancelled() || mExitTasksEarly) {
                value = null;
            }

            final ImageView imageView = getAttachedImageView();
            final View frameView = getAttachedFrameView();
            if (value != null && imageView != null) {
                setImageDrawable(imageView, value);
                if (frameView != null) {
                    frameView.setVisibility(View.GONE);
                }
            }
        }

        @Override
        protected void onCancelled(BitmapDrawable value) {
            super.onCancelled(value);
            synchronized (mPauseWorkLock) {
                mPauseWorkLock.notifyAll();
            }
        }

        private ImageView getAttachedImageView() {
            final ImageView imageView = imageViewReference.get();
            final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

            if (this == bitmapWorkerTask) {
                return imageView;
            }

            return null;
        }

        private View getAttachedFrameView() {
            if (frameViewReference != null) {
                final View view = frameViewReference.get();
                final ImageView imageView = imageViewReference.get();
                final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

                if (this == bitmapWorkerTask) {
                    return view;
                }
            }
            return null;
        }
    }

    private RecyclingBitmapDrawable getIconDrawable(String info) {

        Drawable apkIcon = Utils.getApkIcon(mContext, info);
        if (BitmapDrawable.class.isInstance(apkIcon)) {
            BitmapDrawable icon = (BitmapDrawable) apkIcon;
            if (icon != null)
                return new RecyclingBitmapDrawable(mResources, icon.getBitmap());
            else
                return null;
        } else {
            return null;
        }
    }


    private static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        public AsyncDrawable(Resources res, Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference = new WeakReference<BitmapWorkerTask>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }

    private BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
        BitmapWorkerTask task = null;
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                task = asyncDrawable.getBitmapWorkerTask();
            }
        }
        return task;
    }

    private void setImageDrawable(ImageView imageView, BitmapDrawable drawable) {
        imageView.setImageDrawable(drawable);
    }

    public boolean cancelPotentialWork(Object data, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);
        if (bitmapWorkerTask != null) {
            final Object bitmapData = bitmapWorkerTask.data;
            if (bitmapData == null || !bitmapData.equals(data)) {
                bitmapWorkerTask.cancel(true);
            } else {
                return false;
            }
        }
        return true;
    }


    /**
     * 添加任务
     */
    public void addTask(String info, ImageView imgIcon, Bitmap defaultBitmap, int width, int height, boolean isBig) {

        if (TextUtils.isEmpty(info) || imgIcon == null)
            return;

        BitmapDrawable bitmap = null;
        if (mImageCache != null && !isBig) {
            bitmap = mImageCache.getBitmapFromMemCache(info);
        }
        if (bitmap != null && bitmap.getBitmap() != null && !bitmap.getBitmap().isRecycled()) {
            imgIcon.setImageDrawable(bitmap);
        } else if (cancelPotentialWork(info, imgIcon)) {
            final BitmapWorkerTask workerTask = new BitmapWorkerTask(imgIcon);
            workerTask.isBig = isBig;
            workerTask.data = info;
            workerTask.mWidth = width;
            workerTask.mHeight = height;
            final AsyncDrawable asyncDrawable = new AsyncDrawable(mResources, defaultBitmap, workerTask);
            imgIcon.setImageDrawable(asyncDrawable);
            workerTask.executeOnExecutor(DUAL_THREAD_EXECUTOR, info);
        }
    }

}
