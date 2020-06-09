git -p status -s && perl -wE'say "OK? [y/N]"; <> =~ /y/i or exit(-1)' && git stash push -u -k -m "Pre-commit stash for testing" && lein test && git commit -m "$@" && git stash pop
