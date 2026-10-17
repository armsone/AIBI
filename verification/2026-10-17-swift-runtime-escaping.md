# Stargram Apple expanded runtime syntax correction

The literal-HTML insertion change used CR/LF regexp escapes inside an ordinary Swift multiline string. Single Swift backslashes became actual CR/LF characters in the resulting JavaScript regexp, making the complete observer fail to parse. This explains the missing attachment-scope helper and absence of bridge snapshots.

TM reconstructed the complete observer with provider interpolations replaced by syntax-only literals and native string escapes expanded. Node syntax checking reproduced an invalid regular expression at the insertion regexp. The canonical correction doubles Swift backslashes. Full expanded-runtime syntax, native parsing, build, installed operation and host-result application remain separate evidence levels.

Earlier timing and hidden-clone hypotheses were not confirmed as the cause of the observed failure. The bounded readiness wait and strict visibility changes address independent defensive gaps. Earlier raw-fragment syntax checks did not establish that the native-expanded observer was executable.
