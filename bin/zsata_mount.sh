sudo /sbin/mount.fuse root@192.168.1.124:/zsata/ /zsata -o noauto,_netdev,reconnect,identityfile=/root/.ssh/desktop1_ubuntu20_04_root,allow_other -t fuse.sshfs