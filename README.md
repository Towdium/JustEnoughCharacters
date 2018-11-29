[![][2]][1] 
[![][3]][1]
[![][8]][9]

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

### For developers

This mod provides no API currently, but there will be if there is need
for this. I think most of people will do things in a `contains` way.
So make your mod supported, all you need to do is letting me know the
location of your `contains` call, then the method call will be transformed
to my implementation that supports Chinese. Everything is done.

To keep better compatibility, I would suggest not to let me transform
`contains` called in lambda functions because this mod distinguish methods
by function names and, as you know, the lambda functions have no name.
More specifically, they have automatically generated names which could
change at any time you modify the class. So a suggested way is wrap the
calls into a static method, or whatever with a stable name. A good 
example can be found in [code of Correlated][5], the method `contains`.

If you want to know my implementation of `contains`, here is some words.
It can handle any mixture of English and Chinese, nearly all of the 
circumstances you could meet. For English-only strings, it will work 99%
the same as the original implementation in JRE. If it's not the same,
it should be considered a bug. 

For performance, it has several times the cost of the original one
for English-only strings, and even more time for strings containing
Chinese characters. But after testing, it does not generate significant
lag when used in JEI, so I it should be fine for most of the developers.

### Credits

In versions before 3.0.0, this repository contained a simplified version 
of pinyin4j as pinyin database. Thanks a lot for their work. In versions
after 3.0.0, a new pinyin library is developed and used based on a
mixture of [terra pinyin][6] and [pinyin-data][7].

Have fun!

[1]: https://minecraft.curseforge.com/projects/just-enough-characters
[2]: http://cf.way2muchnoise.eu/full_just-enough-characters_downloads.svg
[3]: http://cf.way2muchnoise.eu/versions/just-enough-characters.svg
[4]: https://github.com/Towdium/JustEnoughCharacters/blob/1.12.0/feed.json
[5]: https://github.com/elytra/Correlated/blob/1.12.1/src/main/java/com/elytradev/correlated/C28n.java
[6]: https://github.com/rime/rime-terra-pinyin
[7]: https://github.com/mozillazg/pinyin-data
[8]: https://img.shields.io/discord/517485644163973120.svg?logo=discord
[9]: https://discord.gg/M3fNfTW
