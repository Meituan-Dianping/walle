# Walle
[![Release Version](https://api.bintray.com/packages/meituan/maven/com.meituan.android.walle:library/images/download.svg)](https://github.com/Meituan-Dianping/walle/releases)
[![Build Status](https://api.travis-ci.org/Meituan-Dianping/walle.svg?branch=master)](https://travis-ci.org/Meituan-Dianping/walle)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/Meituan-Dianping/walle/pulls)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://raw.githubusercontent.com/Meituan-Dianping/walle/master/LICENSE)

Walle（瓦力）：Android Signature V2 Scheme签名下的新一代渠道包打包神器

瓦力通过在Apk中的`APK Signature Block`区块添加自定义的渠道信息来生成渠道包，从而提高了渠道包生成效率，可以作为单机工具来使用，也可以部署在HTTP服务器上来实时处理渠道包Apk的升级网络请求。

## Quick Start
为了方便大家的使用，我们提供了2种使用方式：

* Gradle插件方式，方便快速集成
* 命令行方式，最大化满足各种自定义需求

### Gradle插件使用方式
#### 配置build.gradle

在位于项目的根目录 `build.gradle` 文件中添加Walle Gradle插件的依赖， 如下：

```groovy
buildscript {
    dependencies {
        classpath 'com.meituan.android.walle:plugin:1.1.6'
    }
}
```

并在当前App的 `build.gradle` 文件中apply这个插件，并添加上用于读取渠道号的AAR

```groovy
apply plugin: 'walle'

dependencies {
    compile 'com.meituan.android.walle:library:1.1.6'
}
```

#### 配置插件

```groovy
walle {
    // 指定渠道包的输出路径
    apkOutputFolder = new File("${project.buildDir}/outputs/channels");
    // 定制渠道包的APK的文件名称
    apkFileNameFormat = '${appName}-${packageName}-${channel}-${buildType}-v${versionName}-${versionCode}-${buildTime}.apk';
    // 渠道配置文件
    channelFile = new File("${project.getProjectDir()}/channel")
}
```

配置项具体解释：

* apkOutputFolder：指定渠道包的输出路径， 默认值为`new File("${project.buildDir}/outputs/apk")`
* apkFileNameFormat：定制渠道包的APK的文件名称, 默认值为`'${appName}-${buildType}-${channel}.apk'`  
	可使用以下变量:
                  
	```
	    projectName - 项目名字
	    appName - App模块名字
	    packageName - applicationId (App包名packageName)
	    buildType - buildType (release/debug等)
	    channel - channel名称 (对应渠道打包中的渠道名字)
	    versionName - versionName (显示用的版本号)
	    versionCode - versionCode (内部版本号)
	    buildTime - buildTime (编译构建日期时间)
	    fileSHA1 - fileSHA1 (最终APK文件的SHA1哈希值)
	    flavorName - 编译构建 productFlavors 名
	```  
* channelFile：包含渠道配置信息的文件路径。 具体内容格式详见：[渠道配置文件示例](app/channel)，支持使用#号添加注释。

#### 如何获取渠道信息

在需要渠道等信息时可以通过下面代码进行获取

```java
String channel = WalleChannelReader.getChannel(this.getApplicationContext());
```

#### 如何生成渠道包

生成渠道包的方式是和`assemble${variantName}Channels`指令结合，渠道包的生成目录默认存放在 `build/outputs/apk/`，也可以通过`walle`闭包中的`apkOutputFolder`参数来指定输出目录

用法示例：

* 生成渠道包 `./gradlew clean assembleReleaseChannels`
* 支持 productFlavors `./gradlew clean assembleMeituanReleaseChannels`

#### 更多用法

##### 插入额外信息

`channelFile`只支持渠道写入，如果想插入除渠道以外的其他信息，请在walle配置中使用`configFile`

```
walle {
    // 渠道&额外信息配置文件，与channelFile互斥
	configFile = new File("${project.getProjectDir()}/config.json")
}
```

`configFile`是包含渠道信息和额外信息的配置文件路径。  
配置文件采用json格式，支持为每个channel单独配置额外的写入信息。具体内容格式详见：[渠道&额外信息配置文件示例](app/config.json) 。

注意：

- 此配置项与`channelFile`功能互斥，开发者在使用时选择其一即可，两者都存在时`configFile`优先执行。
- extraInfo 不要出现以`channel`为key的情况

而对应的渠道信息获取方式如下：

```java
ChannelInfo channelInfo= WalleChannelReader.getChannelInfo(this.getApplicationContext());
if (channelInfo != null) {
   String channel = channelInfo.getChannel();
   Map<String, String> extraInfo = channelInfo.getExtraInfo();
}
// 或者也可以直接根据key获取
String value = WalleChannelReader.get(context, "buildtime");
```

##### 临时生成某渠道包

我们推荐使用channelFile/configFile配置来生成渠道包，但有时也可能有临时生成渠道包需求，这时可以使用：

- 生成单个渠道包: `./gradlew clean assembleReleaseChannels -PchannelList=meituan`
- 生成多个渠道包: `./gradlew clean assembleReleaseChannels -PchannelList=meituan,dianping`
- 生成渠道包&写入额外信息:  

  `./gradlew clean assembleReleaseChannels -PchannelList=meituan -PextraInfo=buildtime:20161212,hash:xxxxxxx`  
  
  注意: 这里的extraInfo以`key:value`形式提供，多个以`,`分隔。
- 使用临时channelFile生成渠道包: `./gradlew clean assembleReleaseChannels -PchannelFile=/Users/xx/Documents/channel`
- 使用临时configFile生成渠道包: `./gradlew clean assembleReleaseChannels -PconfigFile=/Users/xx/Documents/config.json`

使用上述-P参数后，本次打包channelFile/configFile配置将会失效，其他配置仍然有效。
`-PchannelList`,`-PchannelFile`, `-PconfigFile`三者不可同时使用。

### 命令行工具使用方式

可以使用命令行工具来支持各类自定义的需求，具体使用方式详见：[Walle CLI 使用说明](walle-cli/README.md)

### 其他使用方式

为了更好的满足大家的各类自定义需求，我们把对`APK Signing Block`区块进行读写操作的模块进行了封装。

读写模块的使用说明详见：

* [APK Signing Block读取模块: payload_reader](payload_reader/README.md)
* [APK Signing Block写入模块: payload_writer](payload_writer/README.md)

## Q&A
- [360加固失效](https://github.com/Meituan-Dianping/walle/wiki/360%E5%8A%A0%E5%9B%BA%E5%A4%B1%E6%95%88%EF%BC%9F)？

## 原理介绍

对该工具的原理感兴趣的同学，可以移步[美团Android新一代渠道包生成工具](http://tech.meituan.com/2017/01/13/android-apk-v2-signature-scheme.html)进行了解。

## 注意事项

* 使用apksigner重新对Apk签名会导致渠道信息丢失，需要再次写入渠道信息
* 1.1.3版本起，walle支持对含有comment的apk进行渠道写入, 详见[issue 52](https://github.com/Meituan-Dianping/walle/issues/52)

## 技术支持

* Read The Fucking Source Code
* 通过提交issue来寻求帮助
* 联系我们寻求帮助

## 贡献代码
* 欢迎提交issue
* 欢迎提交PR

## 参考
* [APK Signature Scheme v2](https://source.android.com/security/apksigning/v2.html)
* [Zip Format](https://en.wikipedia.org/wiki/Zip_(file_format))
* [Android Source Code: ApkSigner](https://android.googlesource.com/platform/build/+/8740e9d)
* [Android Source Code: apksig](https://android.googlesource.com/platform/tools/apksig/)

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
