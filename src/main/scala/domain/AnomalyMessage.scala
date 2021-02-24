package domain

case class AnomalyMessage(targetAppName: String, metricResult: String, meta: AnomalyMessageMetaData)

case class AnomalyMessageMetaData(timeDetectedMillis: String)
