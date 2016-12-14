# 生成cli
./gradlew ::walle-cli:clean :walle-cli:shadowJar
cp walle-cli/build/libs/walle-cli-all.jar walle-cli/walle-cli-all.jar
# 替换readme中的版本
VERSION_NAME=`grep VERSION_NAME gradle.properties`
VERSION_STRING=`echo $VERSION_NAME | cut -d '=' -f 2`
for file in $(find . -name 'README.md'); do
 sed -i.bak -e "s/\(com.meituan.android.walle\):\(.*\):.*/\1:\2:${VERSION_STRING}/g" ${file}
 rm ${file}.bak
done
# 提交
git add .
git commit -m "update cli.jar & readme"
# upload jar & aar
./gradlew clean install
# git tag
#git tag v$VERSION_STRING
