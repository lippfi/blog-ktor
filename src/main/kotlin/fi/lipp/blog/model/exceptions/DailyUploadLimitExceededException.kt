package fi.lipp.blog.model.exceptions

class DailyUploadLimitExceededException(limit: Long, current: Long) : 
    BlogException("Daily upload limit exceeded. Limit: ${limit / 1024 / 1024}MB, Current: ${current / 1024 / 1024}MB", 400)