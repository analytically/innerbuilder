#!/bin/sh

if [ "$TRAVIS_PULL_REQUEST" == "false" ]; then
    echo '######################################'
    echo '#            AFTER SUCCESS           #'
    echo '#             - START -              #'
    echo '######################################'

    echo '---- Cloning repo into /tmp ----'
    cd /tmp
    git clone https://${GH_OAUTH_TOKEN}@github.com/${GH_USER_NAME}/${GH_PROJECT_NAME} innerbuilder 2>&1
    cd innerbuilder

    echo '---- Copy latest build ----'
    cp $CI_HOME/innerbuilder.jar .

    echo '---- Set git settings ----'
    git config --global user.name $GIT_AUTHOR_NAME
    git config --global user.email $GIT_AUTHOR_EMAIL

    echo '---- Add files, commit and push ----'
    git add innerbuilder.jar
    git commit -m "[ci skip] Committed by Travis-CI"
    git push https://${GH_OAUTH_TOKEN}@github.com/${GH_USER_NAME}/${GH_PROJECT_NAME} 2>&1

    echo '######################################'
    echo '#           AFTER SUCCESS            #'
    echo '#            - FINISH -              #'
    echo '######################################'
fi