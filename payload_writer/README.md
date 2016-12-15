# payload_writer

```groovy
dependencies {
    compile 'com.meituan.android.walle:payload_writer:0.1.0
}
```

本库对外提供两个写入类：

- ChannelWriter：向Walle内置id对应区域写入/移除信息，一般存放渠道和额外信息。额外信息key相同时覆盖写入。
- PayloadWriter: 自定义id写入，id相同时覆盖。
  注意：请确保Android未使用此id
