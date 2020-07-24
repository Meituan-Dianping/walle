# payload_reader

```groovy
dependencies {
    compile 'com.meituan.android.walle:payload_reader:1.1.7'
}
```

本库对外提供两个读取类：

- ChannelReader：读取Walle内置id对应的信息，一般用来存放渠道和额外信息。
- PayloadReader: 读取自定义id对应的信息。

## 注意
非Android系统使用此jar包时需要添加org.json依赖

```groovy
compile 'org.json:json:20080701'
```
备注：这么做主要是为了减少android方法数，利用系统自带的org.json
