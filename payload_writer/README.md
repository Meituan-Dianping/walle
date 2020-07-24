# payload_writer

```groovy
dependencies {
    compile 'com.meituan.android.walle:payload_writer:1.1.7'
}
```

本库对外提供两个写入类：

- ChannelWriter：向Walle内置id对应区域写入/移除信息，一般存放渠道和额外信息。额外信息key相同时覆盖写入。
- PayloadWriter: 自定义id写入，id相同时覆盖。
  注意：请确保Android未使用此id

## 注意
非Android系统使用此jar包时需要添加org.json依赖

```groovy
compile 'org.json:json:20080701'
```
备注：这么做主要是为了减少android方法数，利用系统自带的org.json
