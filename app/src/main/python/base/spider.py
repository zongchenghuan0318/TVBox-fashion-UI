import re, json, requests
from lxml import etree
from abc import abstractmethod, ABCMeta
from importlib.machinery import SourceFileLoader


class Spider(metaclass=ABCMeta):
    _instance = None

    def __new__(cls, *args, **kwargs):
        if cls._instance:
            return cls._instance
        cls._instance = super().__new__(cls)
        return cls._instance

    @abstractmethod
    def init(self, extend=''):
        return

    @abstractmethod
    def homeContent(self, filter):
        return

    @abstractmethod
    def homeVideoContent(self):
        return

    @abstractmethod
    def categoryContent(self, tid, pg, filter, extend):
        return

    @abstractmethod
    def detailContent(self, ids):
        return

    @abstractmethod
    def searchContent(self, key, quick):
        return

    def searchContentPage(self, key, quick, pg):
        return

    @abstractmethod
    def playerContent(self, flag, id, vipFlags):
        return

    @abstractmethod
    def localProxy(self, param):
        return

    @abstractmethod
    def isVideoFormat(self, url):
        return

    @abstractmethod
    def manualVideoCheck(self):
        return

    @abstractmethod
    def getName(self):
        return

    def getDependence(self):
        return []

    def regStr(self, src, reg, group=1):
        m = re.search(reg, src)
        src = ""
        if m:
            src = m.group(group)
        return src

    def str2json(self, str):
        return json.loads(str)

    def cleanText(self, src):
        clean = re.sub("[\U0001f600-\U0001f64f\U0001f300-\U0001f5ff\U0001f680-\U0001f6ff\U0001f1e0-\U0001f1ff]", "", src)
        return clean

    def fetch(self, url, cookies=None, headers=None, timeout=5, verify=True, stream=False, allow_redirects=True):
        rsp = requests.get(url, cookies=cookies, headers=headers, timeout=timeout, verify=verify, stream=stream, allow_redirects=allow_redirects)
        rsp.encoding = "utf-8"
        return rsp

    def post(self, url, data, cookies=None, headers=None, timeout=5, verify=True, stream=False, allow_redirects=True):
        rsp = requests.post(url, data=data, cookies=cookies, headers=headers, timeout=timeout, verify=verify, stream=stream, allow_redirects=allow_redirects)
        rsp.encoding = "utf-8"
        return rsp

    def postJson(self, url, json, cookies=None, headers=None, timeout=5, verify=True, stream=False, allow_redirects=True):
        rsp = requests.post(url, json=json, cookies=cookies, headers=headers, timeout=timeout, verify=verify, stream=stream, allow_redirects=allow_redirects)
        rsp.encoding = "utf-8"
        return rsp

    def html(self, content):
        return etree.HTML(content)

    def xpText(self, root, expr):
        ele = root.xpath(expr)
        if len(ele) == 0:
            return ""
        return ele[0]

    def loadModule(self, name, path):
        return SourceFileLoader(name, path).load_module()
