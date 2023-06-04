# helpers.py

import datetime
# import whois
import json
import socket
import time
import traceback
from random import choice
from threading import Thread
from urllib.parse import quote as urlencode
from urllib.parse import unquote
import urllib.request

import pytz
import requests
#import socks
import subprocess
from urllib.error import URLError

#from pytrends.request import TrendReq

from requests import Request, Session
from requests.exceptions import ConnectionError, Timeout, TooManyRedirects


LOG_TRACE = True

TOTAL_WORLD_CAP_TRILLIONS_USD = 116.78  # Source: https://www.statista.com/statistics/274490/global-value-of-share-holdings-since-2000/



def get_pretty_json_string(value):
    return json.dumps(value, indent=4, sort_keys=True, ensure_ascii=False)


def shell(
    shell_command_line: str,
    print_stdout_stderr_bool: bool = True,
    capture_streams_bool: bool = True,
    as_text: bool = True,
    shell_executable_str: str = "bash",
    command_line_flag_str: str = "-c"
):
    result = subprocess.run(
        [shell_executable_str, command_line_flag_str, shell_command_line], 
        stdout = subprocess.PIPE,
        stderr = subprocess.PIPE

        #capture_output=capture_streams_bool, text=as_text  # py3.7+
        )
    if print_stdout_stderr_bool:
        try:
            print(result.stdout.decode('utf-8'))
        except KeyboardInterrupt:
            raise
        except:
            traceback.print_exc()
        try:
            print(result.stderr.decode('utf-8'))
        except KeyboardInterrupt:
            raise
        except:
            traceback.print_exc()
    return result

def fetch_and_compose_gostcoin_price_rur_report(irc_markup_bool, coinmarketcap_apikey):
    boldon = "\x02" if irc_markup_bool else "<b>"
    boldoff = "\x02" if irc_markup_bool else "</b>"
    separ = " | " if irc_markup_bool else """
"""
    separ2 = " | " if irc_markup_bool else """

"""
    try:
        url = "https://api.freiexchange.com/public/ticker/GST"
        print("querying %s" % (url,), flush=True)
        req = urllib.request.Request(
            url,
            data=None,
            headers={
                'User-Agent': 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/35.0.1916.47 Safari/537.36'
            }
        )
        fe_ticker = urllib.request.urlopen(req).read()
        print("fe_ticker (a response):", fe_ticker, flush=True)
        fe_ticker = json.loads(fe_ticker)
        if "GST_BTC" in fe_ticker:
            gst_resp = fe_ticker["GST_BTC"][0]

            highestBuy_lit = gst_resp["highestBuy"]
            highestBuy = float(highestBuy_lit)

            lowestSell_lit = gst_resp["lowestSell"]
            lowestSell = float(lowestSell_lit)

            gst_btc = (highestBuy + lowestSell) * 0.5

        else:
            return f"{boldon}FreiExchange.com error:{boldoff} {str(fe_ticker)}"
    except BaseException as e:
        import traceback as tb
        tb.print_exc()
        raise Exception(f"{boldon}FreiExchange.com error:{boldoff} {str(e)}")
    try:
        url = 'https://pro-api.coinmarketcap.com/v1/cryptocurrency/quotes/latest'
        parameters = {
            'symbol': "BTC",
            'convert': 'RUB'
        }
        headers = {
            'Accepts': 'application/json',
            'X-CMC_PRO_API_KEY': coinmarketcap_apikey,
        }

        session = Session()
        session.headers.update(headers)

        reply_str = f""
        
        print('!курс session.get url=' + url)
        response = session.get(url, params=parameters)
        if LOG_TRACE: print("cmc.text:", response.text)
        cmc = json.loads(response.text)
        if LOG_TRACE: print(f"cmc.json: {get_pretty_json_string(cmc)}")
        btc_rur = float(cmc["data"]["BTC"]["quote"]["RUB"]["price"])
    except (BaseException) as e:
        import traceback
        traceback.print_exc()
        return f"{boldon}CoinMarketCap.com error:{boldoff} {str(e)}"
    return f"{boldon}FreiExchange.com: GST:{boldoff} B {highestBuy_lit}BTC S {lowestSell_lit}BTC {format_currency(gst_btc*btc_rur)}RUR"

def format_currency(value):
    return "{:0,.2f}".format(float(value))
