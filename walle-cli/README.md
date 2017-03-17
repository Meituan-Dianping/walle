# walle-cli

walle-cli是walle提供的命令行程序。

即：本目录下的 walle-cli-all.jar （github release中会及时更新，可到release中下载）

## 使用示例

### 获取信息

显示当前apk中的渠道和额外信息：

```
java -jar walle-cli-all.jar show /Users/Meituan/app/build/outputs/apk/app.apk
```

### 写入信息

写入渠道

```
java -jar walle-cli-all.jar put -c meituan /Users/Meituan/Downloads/app.apk
```

写入额外信息，不提供渠道时不写入渠道

```
java -jar walle-cli-all.jar put -c meituan -e buildtime=20161212,hash=xxxxxxx /Users/xxx/Downloads/app.apk
```

指定输出文件，自定义名称。 不指定时默认与原apk包同目录。

```
java -jar walle-cli-all.jar put -c meituan /Users/Meituan/Downloads/app.apk /Users/xxx/Downloads/app-new-hahha.apk
```

#### 批量写入
##### 命令行指定渠道列表
```
java -jar walle-cli-all.jar batch -c meituan,meituan2,meituan3 /Users/walle/app/build/outputs/apk/app.apk
```

##### 指定渠道配置文件

```
java -jar walle-cli-all.jar batch -f /Users/Meituan/walle/app/channel  /Users/Meituan/walle/app/build/outputs/apk/app.apk
```

[配置文件示例](../app/channel) 支持使用#号添加注释

输出目录可指定，不指定时默认在原apk包同目录下。

##### 指定渠道&额外信息配置文件
```
java -jar walle-cli-all.jar batch2 -f /Users/Meituan/walle/app/config.json  /Users/Meituan/walle/app/build/outputs/apk/app.apk
```

[配置文件示例](../app/config.json)

输出目录可指定，不指定时默认在原apk包同目录下。

## 更多用法

获取cli所有功能

```
java -jar walle-cli-all.jar -h
```

