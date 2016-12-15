# Walle

Walle(瓦力)：支持Android Signature V2 Scheme签名的新一代渠道包打包神器。
Android N推出 [APK Signature Scheme v2](https://source.android.com/security/apksigning/v2.html) 之后，我们之前快速生成渠道包的方式已经行不通了。
这里我们另辟蹊径找到一种简单快速的渠道包生成方式。

![walle.png](assets/walle.png)

## 原理介绍
[美团Android新一代渠道包生成工具](http://tech.meituan.com/android-apk-v2-signature-scheme.html)

## 注意事项
* Android Gradle Plugin的版本必须是2.2.0或者更高
* APK的签名必须 `v2SigningEnabled＝true`
* 使用apksigner重新对apk签名会导致渠道信息丢失，需要再次写入渠道信息

## 使用说明
### 基础使用
#### 根项目的build.gradle配置

在位于项目的根目录 `build.gradle` 文件中添加Walle Gradle插件的依赖， 如下：

```groovy
buildscript {
    dependencies {
        classpath 'com.meituan.android.walle:plugin:0.1.0'
    }
}
```

#### App的build.gradle配置

并在当前App的 `build.gradle` 文件中apply这个插件，并添加上用于读取渠道号的AAR

```groovy
apply plugin: 'walle'

dependencies {
    compile 'com.meituan.android.walle:library:0.1.0'
}
```

#### 生成渠道包

与原assemble指令结合，通过传入参数决定是否生成渠道包

##### 单个渠道生成

```
./gradlew clean assembleRelease -PchannelList=meituan
```

支持 productFlavors

```
./gradlew clean assembleMeituanRelease -PchannelList=meituan
```

##### 多个渠道生成

```
./gradlew clean assembleRelease -PchannelList=meituan,dianping
```

##### 使用渠道配置文件

```
./gradlew clean assembleRelease -PchannelFile=channel
```

配置文件支持相对路径

[配置文件示例](app/channel)   支持使用#号添加注释

##### 渠道包输出目录

build/outputs/apk/

#### 获取渠道信息

在需要渠道等信息时可以通过下面代码进行获取

```java
String channel = WalleChannelReader.getChannel(this.getApplicationContext());
```

### 更多用法

#### 插入额外信息

如果想插入除渠道以外的其他信息，请在生成渠道包时使用

```
./gradlew clean assembleRelease -PchannelList=meituan -PextraInfo=buildtime:20161212,hash:xxxxxxx
```

extraInfo以`key:value`形式提供，多个以`,`分隔。

注意：

- extraInfo需要搭配channelList或者channelFile使用，plugin不支持只写入extraInfo。
- extraInfo 不要出现以`channel`为key的情况

获取：

```java
ChannelInfo channelInfo= WalleChannelReader.getChannelInfo(this.getApplicationContext());
if (channelInfo != null) {
   String channel = channelInfo.getChannel();
   Map<String, String> extraInfo = channelInfo.getExtraInfo();
}
// 或者也可以直接根据key获取
String value = WalleChannelReader.get(context, "buildtime");
```

#### 命令行工具

[Walle CLI 使用说明](walle-cli/README.md)

#### 读写Jar包提供

[读取信息:  payload_reader](payload_reader/README.md)

[写入信息: payload_writer](payload_writer/README.md)

## 技术支持

1. Read The Fucking Source Code
2. 通过提交issue来寻求帮助
3. 联系我们寻求帮助

## 贡献代码
* 欢迎提交issue
* 欢迎提交PR

## 参考
* [APK Signature Scheme v2](https://source.android.com/security/apksigning/v2.html)
* [Zip Format](https://en.wikipedia.org/wiki/Zip_(file_format))
* [Android Source Code: ApkSigner](https://android.googlesource.com/platform/build/+/8740e9d)

## License

    Copyright 2017 Meituan-Dianping

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
