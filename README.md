## Walle

Walle(瓦力)：支持Android Signature V2 Scheme签名的的渠道包打包神器，Android N推出了 [APK Signature Scheme v2](https://source.android.com/security/apksigning/v2.html)， 是我们之前的渠道打包方式都失效了，这里我们另辟蹊径找到一种简单快速的渠道包声称方式。

![walle.png](assets/walle.png) 

## 注意事项
* Android Gradle Plugin的版本必须是2.2.0或者更高
* APK的签名必须 `v2SigningEnabled＝true`


## 使用说明

### 根项目的build.gradle配置

在位于项目的根目录 `build.gradle` 文件中添加walle Gradle插件的依赖， 如下：

```gradle
buildscript {
    dependencies {
        classpath ('com.meituan.android.walle:plugin:0.0.1')
    }
}
```

### App的build.gradle配置

并在当前App的 `build.gradle` 文件中apply这个插件，并添加上用于读取渠道号的AAR

```gradle
apply plugin: 'walle'

dependencies {
    compile('com.meituan.android.walle:library:0.0.1') 
}
```

### 获取渠道信息

在需要渠道等信息时可以通过下面代码进行获取

```java
JsonObject ChannelReader.readChannelInfo()

```

## 技术支持

1. Read The Fuck Source Code
2. 通过提交issue来需求帮助
4. 联系我们需求帮助

## 贡献代码
* 欢迎提交issue
* 欢迎提交PR


## 参考
* [APK Signature Scheme v2](https://source.android.com/security/apksigning/v2.html)
* [Zip Format](https://en.wikipedia.org/wiki/Zip_(file_format))
* [Android Source Code: ApkSigner](https://android.googlesource.com/platform/build/+/8740e9d)

