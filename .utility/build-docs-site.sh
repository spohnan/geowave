#!/bin/bash

if [ "$TRAVIS_BRANCH" == "master" ]
then
    mvn -P docs install
fi
