package com.zhy.imageloader.utils;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.util.LruCache;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by lenovo on 2015/9/8.
 */
public class ImageLoader {
    private static ImageLoader mInstance;

    /**
     * 图片缓存对象
     */
    private LruCache<String, Bitmap> mLrucache;

    /**
     * 线程池
     */
    private ExecutorService mThreadPool;

    /**
     * 默认线程池中线程数
     */
    private static final int DEFAULT_THREAD_COUNT = 1;

    /**
     * 队列的调度方式
     */
    private Type mType = Type.LIFO;

    /**
     * 线程队列
     */
    private LinkedList<Runnable> mTaskQueue;


    /**
     * 后台轮询线程
     */
    private Thread mPoolThread;

    /**
     * 绑定到轮询线程的Handler
     */
    private Handler mPoolThreadHandler;

    /**
     * 更新UI的Handler
     */
    private Handler mUIHandler;

    private ImageLoader(int threadCount, Type type) {
        init(threadCount, type);
    }


    /**
     * 初始化变量
     *
     * @param threadCount
     * @param type
     */
    private void init(int threadCount, Type type) {

        //后台轮询线程
        mPoolThread = new Thread() {
            @Override
            public void run() {
                Looper.prepare();

                mPoolThreadHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        //从线程池中却出一个线程执行
                    }
                };
                Looper.loop();
            }
        };

        mPoolThread.start();

        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        int cacheMemory = maxMemory / 4;

        mLrucache = new LruCache<String, Bitmap>(cacheMemory) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight();
            }
        };

        /**
         * 初始化线程池
         */
        mThreadPool = Executors.newFixedThreadPool(threadCount);
        this.mType = type;

    }


    public static ImageLoader getInstance() {

        if (null == mInstance) {

            synchronized (ImageLoader.class) {
                //两次if判断，防止同步，提高效率
                if (null == mInstance) {
                    mInstance = new ImageLoader(DEFAULT_THREAD_COUNT, Type.LIFO);
                }
            }
        }

        return mInstance;
    }


    /**
     * @param path
     * @param imageView
     */
    public void loadImage(final String path, ImageView imageView) {

        imageView.setTag(path);
        if (null == mUIHandler) {
            mUIHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    ImageInfo imageInfo = (ImageInfo) msg.obj;
                    if (path.equals(imageInfo.path)) {
                        imageInfo.imageView.setImageBitmap(imageInfo.bitmap);
                    }
                }
            };
        }

        Bitmap bitmap = getBitmapFromCache(path);
        if (null != bitmap && !bitmap.isRecycled()) {
            refreshBitmap(path, imageView, bitmap);
        } else {
            addTask(new DisplayImageTask(path, imageView));
            mPoolThreadHandler.sendEmptyMessage(0x110);
        }
    }

    private void refreshBitmap(String path, ImageView imageView, Bitmap bitmap) {
        ImageInfo imageInfo = new ImageInfo();
        imageInfo.path = path;
        imageInfo.bitmap = bitmap;
        imageInfo.imageView = imageView;

        Message message = mUIHandler.obtainMessage();
        message.obj = imageInfo;
        mUIHandler.sendMessage(message);
    }

    private void addTask(Runnable task) {
        mTaskQueue.add(task);
    }

    private Bitmap getBitmapFromCache(String path) {
        return mLrucache.get(path);
    }

    private class ImageInfo {
        String path;
        Bitmap bitmap;
        ImageView imageView;
    }

    public enum Type {
        LIFO,
        FIFO
    }

    public LruCache<String, Bitmap> getmLrucache() {
        return mLrucache;
    }

    private class DisplayImageTask implements Runnable {
        private ImageView imageView;
        private String path;

        public DisplayImageTask(String path, ImageView imageView) {
            this.path = path;
            this.imageView = imageView;
        }

        @Override
        public void run() {
            //加载图片
            ImageSize imageSize = getImageViewSize(imageView);
            //压缩图片
            Bitmap bitmap = compressImage(imageSize, path);
            //加入缓存
            addBitmap2Cache(path, bitmap);

            refreshBitmap(path, imageView, bitmap);

        }

        /**
         * 加入缓存
         *
         * @param path
         * @param bitmap
         */
        private void addBitmap2Cache(String path, Bitmap bitmap) {
            if (ImageLoader.getInstance().getmLrucache().get(path) == null) {
                ImageLoader.getInstance().getmLrucache().put(path, bitmap);
            }
        }

        private Bitmap compressImage(ImageSize imageSize, String path) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);
            int inSampleSize = getInSampleSize(imageSize, options);
            options.inJustDecodeBounds = false;
            options.inSampleSize = inSampleSize;
            return BitmapFactory.decodeFile(path, options);
        }

        private int getInSampleSize(ImageSize imageSize, BitmapFactory.Options options) {
            int reqWidth = imageSize.getWidth();
            int reqHeight = imageSize.getHeight();
            int outHeight = options.outHeight;
            int outWidth = options.outWidth;

            int inSampleSize = 1;
            if (reqWidth < outWidth || reqHeight < outHeight) {
                int widthRadio = (int) Math.round(outWidth * 1.0 / reqWidth);
                int heightRadio = (int) Math.round(outHeight * 1.0 / reqHeight);

                inSampleSize = Math.max(widthRadio, heightRadio);
            }
            return inSampleSize;
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        private ImageSize getImageViewSize(ImageView imageView) {

            ViewGroup.LayoutParams lp = imageView.getLayoutParams();
            DisplayMetrics displayMetrics = imageView.getContext().getResources().getDisplayMetrics();
            //获取实际的宽度
            int width = imageView.getWidth();

            if (width <= 0) {
                //在layout中声明的宽度
                width = lp.width;
            }
            if (width <= 0) {
                //检查最大值
                width = imageView.getMaxWidth();
            }

            if (width <= 0) {
                width = displayMetrics.widthPixels;
            }

            int height = imageView.getHeight();

            if (height <= 0) {
                //在layout中声明的宽度
                height = lp.height;
            }
            if (height <= 0) {
                //检查最大值
                height = imageView.getMaxHeight();
            }

            if (height <= 0) {
                height = displayMetrics.widthPixels;
            }

            ImageSize imageSize = new ImageSize();
            imageSize.setWidth(width);
            imageSize.setHeight(height);
            return imageSize;
        }
    }
}
