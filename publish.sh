./gradlew clean uploadArchives
VERSION_NAME=`grep VERSION_NAME gradle.properties`
VERSION_STRING=`echo $VERSION_NAME | cut -d '=' -f 2`
cd walle-cli
../gradlew clean shadowJar
cp build/libs/walle-cli-all.jar walle-cli-all.jar
git tag v$VERSION_STRING
