SHELL := bash
.ONESHELL:
.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:
MAKEFLAGS += --warn-undefined-variables
MAKEFLAGS += --no-builtin-rules



fe-module := main

ifeq ($(origin .RECIPEPREFIX), undefined)
  $(error This Make does not support .RECIPEPREFIX. Please use GNU Make 4.0 or later)
endif
.RECIPEPREFIX = >

shadow-server:
> yarn
> yarn shadow-cljs server

fe:
> bash ./scripts/start_dev.sh

prod-build: fe-release

shadow-report:
> yarn shadow-cljs run shadow.cljs.build-report $(fe-module) fe-bundle-report.html

watch-$(fe-module):
> yarn shadow-cljs watch :$(fe-module)

watch: watch-$(fe-module)


watch-devcards:
> yarn shadow-cljs watch :devcards


watch-all: watch-$(fe-module)

fe-release:
> bash ./scripts/build_fe_release.sh $(fe-module)



.PHONY: fe fe-release prod-build shadow-report watch-$(fe-module) watch shadow-server



