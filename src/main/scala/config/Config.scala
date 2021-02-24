package config

case class Config(streamConfig: StreamConfig, httpConfig: HttpConfig, serviceDefinitions: ServiceDefinitions)

case class StreamConfig(
    bootstrapServer: String,
    consumerGroup: String,
    topic: String,
    streamSleepTime: Int,
    streamParallelismMax: Int
)

case class HttpConfig(maxConcurrentRequests: Int)

case class ServiceDefinitions(path: String)
