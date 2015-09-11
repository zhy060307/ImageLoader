## 目标

1. 尽可能的避免内存溢出.
    * 压缩图片
    * 使用缓存(LruCache)

2. UI操作尽可能流程.
    * getView里面尽可能不去做耗时操作(异步加载+回调显示)

3. 预期显示的图片尽可能的快(图片加载策略的选择).
    * LIFO(Last In First Out)


## 思路

    ImageLoader

    getView
    {
        根据URL获取Bitmap对象
        步骤：
           1.根据URL在LruCache中查找
           2.找到则返回Bitmap
           3.找不到则根据URL产生Task将其放入TaskQueue且发送消息通知后台轮询线程
    }

    Task-->run(){

        根据URL加载图片
        1.获取图片显示的大小
        2.使用Options对图片进行压缩
        3.加载图片且存入LruCache中

    }

    后台轮询线程(使用Handler+Looper+Message)
    TaskQueue-->Task--->执行线程