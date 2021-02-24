package config

case class Config(streamConfig: StreamConfig, httpConfig: HttpConfig)

case class StreamConfig(
    bootstrapServer: String,
    consumerGroup: String,
    topic: String,
    streamSleepTime: Int,
    streamParallelismMax: Int
)

case class HttpConfig(maxConcurrentRequests: Int)
