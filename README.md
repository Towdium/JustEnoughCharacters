[![][2]][1] 
[![][3]][1]
[![][8]][9]
[中文文档][10]

# Just Enough Characters Mod

## What it does

This is a little mod to make other mods search by pinyin in a Chinese environment. It works for both simplified and traditional Chinese, you can use Quanpin or Phonetic spelling to search. With this mod, you can search by raw text, full pinyin, consonant, with or without tone, or anything you can imagine.

## How it works

This mod is a coremod using a plugin based system for specific targets. It mainly contains following parts:

- __Configurable__: These plugins targets at specific pieces in bytecode according to [online feed][4] fetched at runtime. It includes support for `String.contains`, `Regex.mather`, `StringsKt.contains` and cache structure `net.minecraft.client.util.SuffixArray` provided by vanilla Minecraft.

- __Fixed__: It now includes support for Psi and JEI since their implementation does not match any configurable targets. For JEI, it transfers JEI cache (currently 
`mezz.jei.suffixtree.GeneralizedSuffixTree`) to my cache. For Psi, it transforms Psi's sophisticated ranking system to a simple one based on `String.contains`.

It uses two sets of logic for Pinyin matching:

- __Uncached__: This method is implemented based on NFA for real-time matching. The time complexity is O(sw), with s and w for length of text string and pinyin string. For s=10 and w=10, it does around 1 million matches per second searching in pinyin.

- __Cached__: The cache structure is generalized suffix tree with extra support for pinyin search. The time complexity is O(w+n), with w for length of pinyin string and n for amount of results. For w=10, n=100k, it takes around 10 milliseconds. Space complexity is O(s), with s for amount of characters in total. 

For real time matching like `String.contains`, `Regex.mather` and `StringsKt.contains`, they are transformed to uncached version. For other cache structures, they are replaced by the cached version.

## How to use

For average users, installing it will do all the work. In config file, you can also configure frequently used fuzzy options. For advanced users, there are some tools to play with:

- __/jech profile__: This command collects all methods that can be injected by configurable transformers, then put them in a report.

- __Config__: The config file includes many behaviors to change. You can manually add entry to all the configurable transformers. If you are an experienced programmer, you can easily support other mods according to profiling results. You are welcomed to notify me of these information.

## For developers

This mod provides no API currently, but there will be if there is need
for this. I think most of people will do things in one of configurable methods introduced. So to make your mod supported, all you need to do is letting me know the location of such invokes, I will handle other things.

To keep better compatibility, I would suggest to implement these codes in a named scope, rather than anonymous scopes like lambda because their names can easily change. A good example can be found in [code of Correlated][5], the method `contains`.

If you want to know my implementation of `contains`, here is some words. It can handle any mixture of English and Chinese. For English-only strings, it will work 99% the same as the original implementation in JRE. If it's not the same, it should be considered a bug. For performance, it is slower than the original one, but should not cause you problems.

## Credits

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
[10]: https://github.com/Towdium/JustEnoughCharacters/blob/1.12.0/README_CN.md
