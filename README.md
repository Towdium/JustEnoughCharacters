[![][2]][1] 
[![][3]][1]
[![][4]][5]

# JustEnoughCharacters

## 作用

使用这个模组，你可以在市面上绝大部分模组中使用拼音搜索。这包括了各类模组的手册，物流模组的容器，还有各种稀奇古怪的场景。简体和繁体都可以使用。你可以使用原文，全拼，声母的各种组合进行搜索，你可以使用声调或者忽略声调，任何你能想到的组合都可以使用。模组默认使用全拼拼法，也可以通过修改配置切换到注音或者双拼。当然，双拼场景下字形辅助码是不能用的，但是你可以像其他拼法一样使用声调来过滤。任何不支持的模组搜索都欢迎到 issue 区提给我。

尽管有一些个人实现的代码库已经在Fabric提供了很好的配置文件支持，但为了减少依赖相关方面的原因，jech的fabric版本配置文件是由自己实现的(实现可以在[SimpleJsonConfig][14]找到)

> 一个意外的好处是，和 JEI 一同使用时，JEI 的内存占用可能会缩减 100M 左右。

## 原理

由于核心匹配逻辑已经分离到 [PinIn][9] 这个项目了，本模组当前版本的工作原理极为简单。我们只需要将各模组文本匹配相关的代码找到，然后替换成兼容拼音的实现即可。我们将相关的调用位置填写在 [generate.gradle][10] 里，然后基于 Mixin来替换对应的字节码，使用gradle直接生成所需的 Mixin Class，编译时打入模组包中即可。当然，有一些模组需要特别的兼容处理，这一部分内容你可以在 [这里][11] 找到。

## 开发

尽管直到目前该项目的贡献者屈指可数，给该项目贡献代码仍然是十分简单的。如果你发现有某个模组不支持拼音搜索，你只需要执行 `/jech profile` 命令获得一份全量搜索报告，排查该模组相关的调用栈（需要亿点点技巧），然后提交上来即可。当然，如果能力有限，直接把模组名甩给我也是欢迎的。
请注意，由于Mixin的限制，如果你直接提交对于的调用栈而非全量搜索报告，你需要告诉我目标方法是不是static方法。

## 致谢

- 本模组更新到 1.16 的绝大部分工作是由 [yzl210][8] 完成的。
- 本模组更新到 1.18 的绝大部分工作是由 [yzl210][8] 和 [vfyjxf][13] 完成的。
- 本模组对于 1.16 的一吨 mod 的支持是由 [Death-123][12] 完成的。
- 本模组的核心库 PinIn 中使用的拼音数据来自于 [地球拼音][6] 和 [pinyin-data][7]。

[1]: https://minecraft.curseforge.com/projects/just-enough-characters
[2]: http://cf.way2muchnoise.eu/full_250702_downloads.svg
[3]: http://cf.way2muchnoise.eu/versions/250702.svg
[4]: https://img.shields.io/discord/517485644163973120.svg?logo=discord
[5]: https://discord.gg/M3fNfTW
[6]: https://github.com/rime/rime-terra-pinyin
[7]: https://github.com/mozillazg/pinyin-data
[8]: https://github.com/yzl210
[9]: https://github.com/Towdium/PinIn
[10]: generate.gradle
[11]: src/main/java/me/towdium/jecharacters/mixins/manual
[12]: https://github.com/Death-123
[13]: https://github.com/vfyjxf
[14]: src/main/java/me/towdium/jecharacters/SimpleJsonConfig.java