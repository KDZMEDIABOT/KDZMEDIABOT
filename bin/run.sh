cd /home/user/vcs_rig1/LibreLifeBotTelegram/

sudo /sbin/mount.fuse root@192.168.1.124:/zroot/data/ /zroot/data -o noauto,_netdev,reconnect,identityfile=/root/.ssh/desktop1_ubuntu20_04_root,allow_other -t fuse.sshfs

#nohup sudo -u root bash -c "cd /zroot/data/DK_12TB/hoowiki/ && . ./runsrv.sh"&
#nohup sudo -u root bash -c "cd /zroot/data/DK_12TB/hoowikis2/ && . ./run-hoowikis2.sh"&
#nohup sudo -u root bash -c "cd /zroot/data/bin-wiki-hutor-i2p/moin-1.9 && ./runme.ubuntu20_04.sh"&

echo pwd
pwd
echo whoami
whoami

#. pythonvars.sh
#export PYTHONHOME="/home/user/vcs/LibreLifeBotTelegram/venv"
#export PYTHONPATH="$PYTHONHOME"

# export CL="strace ./venv/bin/python ./launch_all.py"
export CL="./venv_rig1_ubuntu20_04/bin/python3 ./launch_all.py"
echo "launching $CL"
$CL


