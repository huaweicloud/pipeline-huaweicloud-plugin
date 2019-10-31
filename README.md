# pipeline-huaweicloud-plugin
This plugins adds Jenkins pipeline steps to interact with the HuaweiCloud API.


# Usage / Steps

## withOBS

the `withOBS` step provides authorization for the nested steps.

Set region, endpointUrl, credentials information:

```groovy
 withOBS(endpointUrl:"https://obs.cn-north-1.myhuaweicloud.com",region:'cn-north-1',credentials:'ZJTEST') {
    // do something
}
```

When you use Jenkins Declarative Pipelines you can also use `withOBS` in an options block:

```groovy
options {
	withOBS(endpointUrl:"https://obs.cn-north-1.myhuaweicloud.com",region:'cn-north-1',credentials:'ZJTEST')
}
stages {
	...
}
```
## s3Upload

Upload a file from the workspace (or a String) to an OBS bucket.
```groovy
options {
  withOBS(endpointUrl:"https://obs.cn-north-1.myhuaweicloud.com",region:'cn-north-1',credentials:'ZJTEST')
}
steps {
  obsUpload(file:'ploaded5959630964693432219.jpi', bucket:'obs-test', path:'/')
}
```


## invokeFunction

Invoke a function.

The step returns the object returned by the function.
```groovy
steps {
  script {
    def result = invokeFunction(functionName: 'test002', payloadAsString: '{"key": "value"}')
    echo "Testing the ${result} browser"
 }
}
```
