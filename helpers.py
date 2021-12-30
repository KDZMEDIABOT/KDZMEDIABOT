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

import pytz
import requests
import socks
import subprocess
from urllib.error import URLError

from pytrends.request import TrendReq


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

