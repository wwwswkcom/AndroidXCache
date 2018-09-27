package cn.xcache;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author xurunjie
 * @description 缓存类
 * @date 2017/09/26
 */
public class XCache {
    /**
     * 缓存时间
     */
    private int time;
    /**
     * 缓存内存
     */
    private int size;
    /**
     * 缓存数量
     */
    private  int count;
    /**
     * 缓存管理类
     */
    private XCacheManager mCache;

    /**
     * 默认路径
     */
    private String path;

    private XCache(Builder builder,Context context) {
        time = builder.time;
        size = builder.size;
        count = builder.count;
        path = builder.path;
        File cacheDir = new File(context.getCacheDir(), path);
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            throw new RuntimeException("can't make dirs in " + cacheDir.getAbsolutePath());
        }
        mCache = new XCacheManager(cacheDir,size,count);
    }

    /**
     * builder模式
     */
    public static class Builder{
        /**
         * 缓存默认不限时间 单位秒
         */
        public int time = -1;
        /**
         * 缓存默认内存10m
         */
        private int size = 1000 * 1000 * 10;
        /**
         * 缓存默认不限条数
         */
        private  int count = Integer.MAX_VALUE;
        /**
         * 默认路径
         */
        private String path = "xcache";
        /**
         * 上下文
         */
        private Context context;

        /**
         * 上下文
         * @param context
         */
        public Builder(Context context){
            this.context = context;
        }
        public Builder time(int time){
            this.time = time;
            return this;
        }
        public Builder size(int size){
            this.size = size;
            return this;
        }
        public Builder count(int count){
            this.count = count;
            return this;
        }
        public Builder path(String path){
            this.path = path;
            return this;
        }
        public XCache build() {
            return new XCache(this, context);
        }
    }
    /**
     * 移除某个key
     *
     * @param key
     */
    public void remove(String key) {
        mCache.remove(key);
    }

    /**
     * 清除所有数据
     */
    public void clear() {
        mCache.clear();
    }
    // ============ String数据 读写 ==============
    /**
     * 保存 String数据 到 缓存中
     *
     * @param key
     *            保存的key
     * @param value
     *            保存的String数据
     */
    public void put(String key, String value) {
        value = XCacheUtils.newStringWithDateInfo(time, value);
        File file = mCache.newFile(key);
        BufferedWriter out = null;
        try {
            out = new BufferedWriter(new FileWriter(file), 1024);
            out.write(value);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.flush();
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            mCache.put(file);
        }
    }
    /**
     * 保存 String数据 到 缓存中
     *
     * @param key
     *            保存的key
     * @param value
     *            保存的String数据
     * @param saveTime
     *            保存的时间，单位：秒
     */
    public void put(String key, String value, int saveTime) {
        put(key, XCacheUtils.newStringWithDateInfo(saveTime, value));
    }

    /**
     * 读取 String数据
     *
     * @param key key值
     * @return String 数据
     */
    public String getString(String key) {
        File file = mCache.get(key);
        if (!file.exists()){
            return null;
        }
        BufferedReader in = null;
        boolean isOutOfData = false;
        try {
            in = new BufferedReader(new FileReader(file));
            String readString = "";
            String currentLine;
            while ((currentLine = in.readLine()) != null) {
                readString += currentLine;
            }
            if (!XCacheUtils.isDue(readString)) {
                return XCacheUtils.clearDateInfo(readString);
            } else {
                isOutOfData = true;
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (isOutOfData){
                remove(key);
            }
        }
    }
    // ============= JSONObject 数据 读写 ==============
    /**
     * 保存 JSONObject数据 到 缓存中
     *
     * @param key
     *            保存的key
     * @param value
     *            保存的JSON数据
     */
    public void put(String key, JSONObject value) {
        put(key, value.toString());
    }

    /**
     * 保存 JSONObject数据 到 缓存中
     *
     * @param key
     *            保存的key
     * @param value
     *            保存的JSONObject数据
     * @param saveTime
     *            保存的时间，单位：秒
     */
    public void put(String key, JSONObject value, int saveTime) {
        put(key, value.toString(), saveTime);
    }

    /**
     * 读取JSONObject数据
     *
     * @param key
     * @return JSONObject数据
     */
    public JSONObject getJSONObject(String key) {
        String jsonString = getString(key);
        if (TextUtils.isEmpty(jsonString)){
            return null;
        }
        try {
            JSONObject obj = new JSONObject(jsonString);
            return obj;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    // ============ JSONArray 数据 读写 =============
    /**
     * 保存 JSONArray数据 到 缓存中
     *
     * @param key
     *            保存的key
     * @param value
     *            保存的JSONArray数据
     */
    public void put(String key, JSONArray value) {
        put(key, value.toString());
    }

    /**
     * 保存 JSONArray数据 到 缓存中
     *
     * @param key
     *            保存的key
     * @param value
     *            保存的JSONArray数据
     * @param saveTime
     *            保存的时间，单位：秒
     */
    public void put(String key, JSONArray value, int saveTime) {
        put(key, value.toString(), saveTime);
    }

    /**
     * 读取JSONArray数据
     *
     * @param key
     * @return JSONArray数据
     */
    public JSONArray getJSONArray(String key) {
        String jsonString = getString(key);
        if (TextUtils.isEmpty(jsonString)){
            return null;
        }
        try {
            JSONArray obj = new JSONArray(jsonString);
            return obj;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    // ============== byte 数据 读写 =============
    /**
     * 保存 byte数据 到 缓存中
     *
     * @param key
     *            保存的key
     * @param value
     *            保存的数据
     */
    public void put(String key, byte[] value) {
        value = XCacheUtils.newByteArrayWithDateInfo(time, value);
        File file = mCache.newFile(key);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            out.write(value);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.flush();
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            mCache.put(file);
        }
    }
    /**
     * 保存 byte数据 到 缓存中
     *
     * @param key
     *            保存的key
     * @param value
     *            保存的数据
     * @param saveTime
     *            保存的时间，单位：秒
     */
    public void put(String key, byte[] value, int saveTime) {
        put(key, XCacheUtils.newByteArrayWithDateInfo(saveTime, value));
    }

    /**
     * 获取 byte 数据
     *
     * @param key
     * @return byte 数据
     */
    public byte[] getBinary(String key) {
        RandomAccessFile raFile = null;
        boolean removeFile = false;
        try {
            File file = mCache.get(key);
            if (!file.exists()){
                return null;
            }
            raFile = new RandomAccessFile(file, "r");
            byte[] byteArray = new byte[(int) raFile.length()];
            raFile.read(byteArray);
            if (!XCacheUtils.isDue(byteArray)) {
                return XCacheUtils.clearDateInfo(byteArray);
            } else {
                removeFile = true;
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            if (raFile != null) {
                try {
                    raFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (removeFile){
                remove(key);
            }
        }
    }
    // ============= 序列化 数据 读写 ===============
    /**
     * 保存 Serializable数据 到 缓存中
     *
     * @param key
     *            保存的key
     * @param value
     *            保存的value
     */
    public void put(String key, Serializable value) {
        put(key, value, -1);
    }

    /**
     * 保存 Serializable数据到 缓存中
     *
     * @param key
     *            保存的key
     * @param value
     *            保存的value
     * @param saveTime
     *            保存的时间，单位：秒
     */
    public void put(String key, Serializable value, int saveTime) {
        ByteArrayOutputStream baos ;
        ObjectOutputStream oos = null;
        try {
            baos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(baos);
            oos.writeObject(value);
            byte[] data = baos.toByteArray();
            if (saveTime != -1) {
                put(key, data, saveTime);
            } else {
                put(key, data);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                oos.close();
            } catch (IOException e) {
            }
        }
    }

    /**
     * 读取 Serializable数据
     *
     * @param key
     * @return Serializable 数据
     */
    public Object getObject(String key) {
        byte[] data = getBinary(key);
        if (data != null) {
            ByteArrayInputStream bais = null;
            ObjectInputStream ois = null;
            try {
                bais = new ByteArrayInputStream(data);
                ois = new ObjectInputStream(bais);
                Object reObject = ois.readObject();
                return reObject;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            } finally {
                try {
                    if (bais != null){
                        bais.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    if (ois != null){
                        ois.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;

    }
    // ============== bitmap 数据 读写 =============
    /**
     * 保存 bitmap 到 缓存中
     *
     * @param key
     *            保存的key
     * @param value
     *            保存的bitmap数据
     */
    public void put(String key, Bitmap value) {
        put(key, XCacheUtils.bitmap2Bytes(value));
    }

    /**
     * 保存 bitmap 到 缓存中
     *
     * @param key
     *            保存的key
     * @param value
     *            保存的 bitmap 数据
     * @param saveTime
     *            保存的时间，单位：秒
     */
    public void put(String key, Bitmap value, int saveTime) {
        put(key, XCacheUtils.bitmap2Bytes(value), saveTime);
    }

    /**
     * 读取 bitmap 数据
     *
     * @param key
     * @return bitmap 数据
     */
    public Bitmap getBitmap(String key) {
        if (getBinary(key) == null) {
            return null;
        }
        return XCacheUtils.bytes2Bimap(getBinary(key));
    }
    // ============= drawable 数据 读写 =============
    /**
     * 保存 drawable 到 缓存中
     *
     * @param key
     *            保存的key
     * @param value
     *            保存的drawable数据
     */
    public void put(String key, Drawable value) {
        put(key, XCacheUtils.drawable2Bitmap(value));
    }

    /**
     * 保存 drawable 到 缓存中
     *
     * @param key
     *            保存的key
     * @param value
     *            保存的 drawable 数据
     * @param saveTime
     *            保存的时间，单位：秒
     */
    public void put(String key, Drawable value, int saveTime) {
        put(key, XCacheUtils.drawable2Bitmap(value), saveTime);
    }

    /**
     * 读取 Drawable 数据
     *
     * @param key
     * @return Drawable 数据
     */
    public Drawable getDrawable(String key) {
        if (getBinary(key) == null) {
            return null;
        }
        return XCacheUtils.bitmap2Drawable(XCacheUtils.bytes2Bimap(getBinary(key)));
    }

    /**
     * @author xurunjie
     * @description
     * @date 2017/09/26
     */
    private class XCacheManager {

        private final AtomicLong cacheSize;
        private final AtomicInteger cacheCount;
        private final long sizeLimit;
        private final int countLimit;
        private final Map<File, Long> lastUsageDates = Collections.synchronizedMap(new HashMap<File, Long>());
        private File cacheDir;

        /**
         * 构造
         *
         * @param cacheDir   缓存路径
         * @param sizeLimit  内存限制
         * @param countLimit 数量限制
         */
        private XCacheManager(File cacheDir, long sizeLimit, int countLimit) {
            this.cacheDir = cacheDir;
            this.sizeLimit = sizeLimit;
            this.countLimit = countLimit;
            cacheSize = new AtomicLong();
            cacheCount = new AtomicInteger();
            calculateCacheSizeAndCacheCount();
        }

        /**
         * 计算 cacheSize和cacheCount
         */
        private void calculateCacheSizeAndCacheCount() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    int size = 0;
                    File[] cachedFiles = cacheDir.listFiles();
                    if (cachedFiles != null) {
                        count = cachedFiles.length;
                        for (File cachedFile : cachedFiles) {
                            size += calculateSize(cachedFile);
                            lastUsageDates.put(cachedFile, cachedFile.lastModified());
                        }
                        cacheSize.set(size);
                        cacheCount.set(count);
                    }
                }
            }).start();
        }

        /**
         * 添加文件
         * 这里存在一个问题，相同文件大小发生变化时可能出现超出内存的问题
         * 由于每次重新计算内存消耗太大了，权衡之下采取此种方案
         * @param file
         */
        private void put(File file) {
            //不相同的文件才做处理了处理
            if (lastUsageDates.get(file)==null){
                int curCacheCount = cacheCount.get();
                while (curCacheCount + 1 > countLimit) {
                    long freedSize = removeNext();
                    cacheSize.addAndGet(-freedSize);

                    curCacheCount = cacheCount.addAndGet(-1);
                }
                cacheCount.addAndGet(1);

                long valueSize = calculateSize(file);
                long curCacheSize = cacheSize.get();
                while (curCacheSize + valueSize > sizeLimit) {
                    long freedSize = removeNext();
                    curCacheSize = cacheSize.addAndGet(-freedSize);
                    cacheCount.addAndGet(-1);
                }
                cacheSize.addAndGet(valueSize);
            }
            Long currentTime = System.currentTimeMillis();
            file.setLastModified(currentTime);
            lastUsageDates.put(file, currentTime);
        }

        /**
         * 获取文件并更新时间
         *
         * @param key
         * @return
         */
        private File get(String key) {
            File file = newFile(key);
            //只有已经存在的数据，更新时间
            if (lastUsageDates.get(file)!=null){
                Long currentTime = System.currentTimeMillis();
                file.setLastModified(currentTime);
                lastUsageDates.put(file, currentTime);
            }
            return file;
        }

        /**
         * 重建文件 ，这边不采用key..hashCode()的方式，是避免hash值相同造成错误
         * @param key
         * @return
         */
        private File newFile(String key) {
            return new File(cacheDir, key);
        }

        /**
         * 删除指定内容
         *
         * @param key
         */
        private void remove(String key) {
            File file = newFile(key);
            ;
            long fileSize = calculateSize(file);
            if (file.delete()) {
                lastUsageDates.remove(file);
            }
            cacheSize.addAndGet(-fileSize);
            cacheCount.addAndGet(-1);
        }

        /**
         * 清理缓存
         */
        private void clear() {
            lastUsageDates.clear();
            cacheSize.set(0);
            cacheCount.set(0);
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        }

        /**
         * 移除旧的文件
         *
         * @return
         */
        private long removeNext() {
            if (lastUsageDates.isEmpty()) {
                return 0;
            }

            Long oldestUsage = null;
            File mostLongUsedFile = null;
            Set<Map.Entry<File, Long>> entries = lastUsageDates.entrySet();
            synchronized (lastUsageDates) {
                for (Map.Entry<File, Long> entry : entries) {
                    if (mostLongUsedFile == null) {
                        mostLongUsedFile = entry.getKey();
                        oldestUsage = entry.getValue();
                    } else {
                        Long lastValueUsage = entry.getValue();
                        if (lastValueUsage < oldestUsage) {
                            oldestUsage = lastValueUsage;
                            mostLongUsedFile = entry.getKey();
                        }
                    }
                }
            }

            long fileSize = calculateSize(mostLongUsedFile);
            if (mostLongUsedFile.delete()) {
                lastUsageDates.remove(mostLongUsedFile);
            }
            return fileSize;
        }

        /**
         * 文件大小
         *
         * @param file
         * @return
         */
        private long calculateSize(File file) {
            return file.length();
        }
    }


    /**
     * 时间计算工具类
     *
     * @author xurunjie
     * @description
     * @date 2017/09/26
     */
    private static class XCacheUtils {
        /**
         * 判断缓存的String数据是否到期
         *
         * @param str
         * @return true：到期了 false：还没有到期
         */
        private static boolean isDue(String str) {
            return isDue(str.getBytes());
        }

        /**
         * 判断缓存的byte数据是否到期
         *
         * @param data
         * @return true：到期了 false：还没有到期
         */
        private static boolean isDue(byte[] data) {
            String[] strs = getDateInfoFromDate(data);
            if (strs != null && strs.length == 2) {
                String saveTimeStr = strs[0];
                while (saveTimeStr.startsWith("0")) {
                    saveTimeStr = saveTimeStr.substring(1, saveTimeStr.length());
                }
                long saveTime = Long.valueOf(saveTimeStr);
                long deleteAfter = Long.valueOf(strs[1]);
                if (System.currentTimeMillis() > saveTime + deleteAfter * 1000) {
                    return true;
                }
            }
            return false;
        }

        private static String newStringWithDateInfo(int second, String strInfo) {
            if (second>0){
                return createDateInfo(second) + strInfo;
            }
            return "";
        }

        private static byte[] newByteArrayWithDateInfo(int second, byte[] data2) {
            if (second>0){
                byte[] data1 = createDateInfo(second).getBytes();
                byte[] retdata = new byte[data1.length + data2.length];
                System.arraycopy(data1, 0, retdata, 0, data1.length);
                System.arraycopy(data2, 0, retdata, data1.length, data2.length);
                return retdata;
            }
            return data2;

        }

        private static String clearDateInfo(String strInfo) {
            if (strInfo != null && hasDateInfo(strInfo.getBytes())) {
                strInfo = strInfo.substring(strInfo.indexOf(M_SEPARATOR) + 1, strInfo.length());
            }
            return strInfo;
        }

        private static byte[] clearDateInfo(byte[] data) {
            if (hasDateInfo(data)) {
                return copyOfRange(data, indexOf(data, M_SEPARATOR) + 1, data.length);
            }
            return data;
        }

        private static boolean hasDateInfo(byte[] data) {
            return data != null && data.length > 15 && data[13] == '-' && indexOf(data, M_SEPARATOR) > 14;
        }

        private static String[] getDateInfoFromDate(byte[] data) {
            if (hasDateInfo(data)) {
                String saveDate = new String(copyOfRange(data, 0, 13));
                String deleteAfter = new String(copyOfRange(data, 14, indexOf(data, M_SEPARATOR)));
                return new String[] {saveDate, deleteAfter};
            }
            return null;
        }

        private static int indexOf(byte[] data, char c) {
            for (int i = 0; i < data.length; i++) {
                if (data[i] == c) {
                    return i;
                }
            }
            return -1;
        }

        private static byte[] copyOfRange(byte[] original, int from, int to) {
            int newLength = to - from;
            if (newLength < 0) {
                throw new IllegalArgumentException(from + " > " + to);
            }
            byte[] copy = new byte[newLength];
            System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
            return copy;
        }

        private static final char M_SEPARATOR = ' ';

        private static String createDateInfo(int second) {
            String currentTime = System.currentTimeMillis() + "";
            while (currentTime.length() < 13) {
                currentTime = "0" + currentTime;
            }
            return currentTime + "-" + second + M_SEPARATOR;
        }

        /**
         * bitmap转化为字节
         *
         * @param bm
         * @return
         */
        private static byte[] bitmap2Bytes(Bitmap bm) {
            if (bm == null) {
                return null;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.PNG, 100, baos);
            return baos.toByteArray();
        }

        /**
         * 字节转化为bitmap
         *
         * @param b
         * @return
         */
        private static Bitmap bytes2Bimap(byte[] b) {
            if (b.length == 0) {
                return null;
            }
            return BitmapFactory.decodeByteArray(b, 0, b.length);
        }

        /**
         * bitmap获取
         *
         * @param drawable 参数
         * @return
         */
        private static Bitmap drawable2Bitmap(Drawable drawable) {
            if (drawable == null) {
                return null;
            }
            // 取 drawable 的长宽
            int w = drawable.getIntrinsicWidth();
            int h = drawable.getIntrinsicHeight();
            // 取 drawable 的颜色格式
            Bitmap.Config config = drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888
                : Bitmap.Config.RGB_565;
            // 建立对应 bitmap
            Bitmap bitmap = Bitmap.createBitmap(w, h, config);
            // 建立对应 bitmap 的画布
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, w, h);
            // 把 drawable 内容画到画布中
            drawable.draw(canvas);
            return bitmap;
        }

        /**
         * 获取 drawable
         *
         * @param bm 参数
         * @return
         */
        private static Drawable bitmap2Drawable(Bitmap bm) {
            if (bm == null) {
                return null;
            }
            BitmapDrawable bd = new BitmapDrawable(bm);
            bd.setTargetDensity(bm.getDensity());
            return new BitmapDrawable(bm);
        }
    }
}

