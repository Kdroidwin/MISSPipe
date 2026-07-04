class NotFound(Exception):
    def __init__(self, msg: str):
        self.msg = msg


class NetworkError(Exception):
    def __init__(self, msg: str):
        self.msg = msg


class BotDetection(Exception):
    def __init__(self, msg: str):
        self.msg = msg


class ProxyError(Exception):
    def __init__(self, msg: str):
        self.msg = msg


class UnknownNetworkError(Exception):
    def __init__(self, msg):
        self.msg = msg