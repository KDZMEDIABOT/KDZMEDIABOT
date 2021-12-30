cd /vcs/LibreLifeBotTelegram/

echo pwd
pwd
echo whoami
whoami

. pythonvars.sh

# export CL="strace ./venv/bin/python ./launch_all.py"
export CL="./venv/bin/python ./launch_all.py"
echo "launching $CL"
$CL


