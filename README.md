[![][2]][1] 
[![][3]][1]

# Just Enough Characters Mod

### What it does

This is a little mod to help JEI search by pinyin in a Chinese environment.  
The developer uses simplified Chinese
but it should also work for traditional Chinese (not tested).  
With this mod, you can search by full pinyin, 
consonant or any combination of both (全拼，声母或两者的任意组合).

### How does it work

This mod is a coremod using a plugin based system for class transformation.  
The plugins present are listed as follows:

- __Vanilla__: Transfer vanilla minecraft after 1.12 for creative and recipe
book search. It substitutes the entire cache system in
`net.minecraft.client.util.SuffixArray` to my universal cache to support
pinyin search.

- __JEI__: Transfers JEI cache (currently in 
`mezz.jei.suffixtree.GeneralizedSuffixTree`) to my cache so you can use
pinyin to search. It used to be simple, before the day when mezz decides
to use the current cache.

- __String.contains__: This method is used for most of other mods that use
`String.contains` for string matching. The transformed methods can be
customized in config file. The local data will sync with online data from
[online feed][4].

- __Regex.matcher__: RegExp is the method used in few mods like NEI and AE.
My method is transforming the `Regex.mather` call to my fake Matcher.
Really, don't expect me to enable pinyin search in RegExp. So, by enabling
transforming of RegExp methods, you can do pinyin, but no more RegExp.

- __Radical__: Yes it transforms all the calls to `String.contains`.
So it can support most of mods even without special support. But it also
generates many bugs. Testing only.

- __Dumper__: It does not do transformation. It will only dump all the
methods all methods in a class, which you can specify in config.
Very useful when profiling.

### How to use

For average users, installing it will do all the work. For advanced
users, there are some tools to play with:

##### /jech profile

It collects all methods with `String.contains` call and `Regex.matcher` 
call then put them in a report, of which the format is optimized for
machines to read.

##### Config

You can manually add entry to support `String.contains` and `Regex.matcher`
call. If you are an experienced programmer, you can easily support other
mods that is not currently supported. You are welcomed to notify me of
these information.

### Credits

This repository contains a simplified version of pinyin4j as pinyin
database. Thanks a lot for their work. 

By the way, since pinyin4j uses GPL as its license, I have to use 
it in this project. Apologies for any inconvenience. Also, any 
implementation of pinyin database with looser license is welcomed as
pull requests. Them I can move to looser licenses (LGPL possibly).

Have fun!

[1]: https://minecraft.curseforge.com/projects/just-enough-characters
[2]: http://cf.way2muchnoise.eu/full_just-enough-characters_downloads.svg
[3]: http://cf.way2muchnoise.eu/versions/just-enough-characters.svg
[4]: https://github.com/Towdium/JustEnoughCharacters/blob/1.12.0/feed.json