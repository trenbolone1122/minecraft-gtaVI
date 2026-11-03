# shader module: credits and third party notices

## Leonida Shaders (our pack)
`src/main/resources/assets/leonida/shader/pack/` is an original Iris/OptiFine format shader pack written for this mod,
GPL-3.0-or-later like the rest of Leonida. No code was copied from other shader packs (Complementary, BSL and others
forbid it). Techniques are standard and public: single scattering sky (Rayleigh, Mie, ozone), golden angle PCF soft
shadows with shadow map distortion, octahedral normal packing, interleaved gradient noise (Jimenez 2014), the ACES
filmic curve fit by Krzysztof Narkowicz, labPBR 1.3 material format (shaderLABS community standard).
Sky and colour targets follow /home/user/workspace/mcmods/LOOK.md (leonidastate3d lighting recipe).

## Bundled mods (jar-in-jar, unmodified)
The Leonida jar nests two unmodified third party mods under META-INF/jars/ so players install one file. Fabric Loader
loads the newest version if a player installs Iris or Sodium separately, so either can be replaced or updated freely.

### Iris 1.11.7+26.3-fabric
- Authors: coderbot, IMS212, Justsnoopy30, FoundationGames and contributors. https://github.com/IrisShaders/Iris
- License: GNU Lesser General Public License v3.0 only (LGPL-3.0-only). Corresponding source:
  https://github.com/IrisShaders/Iris (tag for 1.11.7, Minecraft 26.3). Binary from the Modrinth maven
  (maven.modrinth:iris:1.11.7+26.3-fabric). The LGPL builds on the GNU GPL v3, whose full text ships as LICENSE_leonida.
- Iris nests its own libraries (antlr4-runtime BSD-3-Clause, glsl-transformer MIT, jcpp Apache-2.0, Fabric API modules
  Apache-2.0) with their notices inside its jar.

### Sodium mc26.3-0.9.3-beta.1-fabric
- Required Notice: Copyright JellySquid (jellysquid3) and the CaffeineMC contributors. https://github.com/CaffeineMC/sodium
- License: PolyForm Shield License 1.0.0 (https://polyformproject.org/licenses/shield/1.0.0). Distribution is allowed
  when these terms are passed on; the license forbids using Sodium to make a product that competes with it, which this
  mod does not. Binary from the Modrinth maven (maven.modrinth:sodium:mc26.3-0.9.3-beta.1-fabric), unmodified.

## License texts

### GNU Lesser General Public License v3.0 (Iris)
```
                   GNU LESSER GENERAL PUBLIC LICENSE
                       Version 3, 29 June 2007

 Copyright (C) 2007 Free Software Foundation, Inc. <https://fsf.org/>
 Everyone is permitted to copy and distribute verbatim copies
 of this license document, but changing it is not allowed.


  This version of the GNU Lesser General Public License incorporates
the terms and conditions of version 3 of the GNU General Public
License, supplemented by the additional permissions listed below.

  0. Additional Definitions.

  As used herein, "this License" refers to version 3 of the GNU Lesser
General Public License, and the "GNU GPL" refers to version 3 of the GNU
General Public License.

  "The Library" refers to a covered work governed by this License,
other than an Application or a Combined Work as defined below.

  An "Application" is any work that makes use of an interface provided
by the Library, but which is not otherwise based on the Library.
Defining a subclass of a class defined by the Library is deemed a mode
of using an interface provided by the Library.

  A "Combined Work" is a work produced by combining or linking an
Application with the Library.  The particular version of the Library
with which the Combined Work was made is also called the "Linked
Version".

  The "Minimal Corresponding Source" for a Combined Work means the
Corresponding Source for the Combined Work, excluding any source code
for portions of the Combined Work that, considered in isolation, are
based on the Application, and not on the Linked Version.

  The "Corresponding Application Code" for a Combined Work means the
object code and/or source code for the Application, including any data
and utility programs needed for reproducing the Combined Work from the
Application, but excluding the System Libraries of the Combined Work.

  1. Exception to Section 3 of the GNU GPL.

  You may convey a covered work under sections 3 and 4 of this License
without being bound by section 3 of the GNU GPL.

  2. Conveying Modified Versions.

  If you modify a copy of the Library, and, in your modifications, a
facility refers to a function or data to be supplied by an Application
that uses the facility (other than as an argument passed when the
facility is invoked), then you may convey a copy of the modified
version:

   a) under this License, provided that you make a good faith effort to
   ensure that, in the event an Application does not supply the
   function or data, the facility still operates, and performs
   whatever part of its purpose remains meaningful, or

   b) under the GNU GPL, with none of the additional permissions of
   this License applicable to that copy.

  3. Object Code Incorporating Material from Library Header Files.

  The object code form of an Application may incorporate material from
a header file that is part of the Library.  You may convey such object
code under terms of your choice, provided that, if the incorporated
material is not limited to numerical parameters, data structure
layouts and accessors, or small macros, inline functions and templates
(ten or fewer lines in length), you do both of the following:

   a) Give prominent notice with each copy of the object code that the
   Library is used in it and that the Library and its use are
   covered by this License.

   b) Accompany the object code with a copy of the GNU GPL and this license
   document.

  4. Combined Works.

  You may convey a Combined Work under terms of your choice that,
taken together, effectively do not restrict modification of the
portions of the Library contained in the Combined Work and reverse
engineering for debugging such modifications, if you also do each of
the following:

   a) Give prominent notice with each copy of the Combined Work that
   the Library is used in it and that the Library and its use are
   covered by this License.

   b) Accompany the Combined Work with a copy of the GNU GPL and this license
   document.

   c) For a Combined Work that displays copyright notices during
   execution, include the copyright notice for the Library among
   these notices, as well as a reference directing the user to the
   copies of the GNU GPL and this license document.

   d) Do one of the following:

       0) Convey the Minimal Corresponding Source under the terms of this
       License, and the Corresponding Application Code in a form
       suitable for, and under terms that permit, the user to
       recombine or relink the Application with a modified version of
       the Linked Version to produce a modified Combined Work, in the
       manner specified by section 6 of the GNU GPL for conveying
       Corresponding Source.

       1) Use a suitable shared library mechanism for linking with the
       Library.  A suitable mechanism is one that (a) uses at run time
       a copy of the Library already present on the user's computer
       system, and (b) will operate properly with a modified version
       of the Library that is interface-compatible with the Linked
       Version.

   e) Provide Installation Information, but only if you would otherwise
   be required to provide such information under section 6 of the
   GNU GPL, and only to the extent that such information is
   necessary to install and execute a modified version of the
   Combined Work produced by recombining or relinking the
   Application with a modified version of the Linked Version. (If
   you use option 4d0, the Installation Information must accompany
   the Minimal Corresponding Source and Corresponding Application
   Code. If you use option 4d1, you must provide the Installation
   Information in the manner specified by section 6 of the GNU GPL
   for conveying Corresponding Source.)

  5. Combined Libraries.

  You may place library facilities that are a work based on the
Library side by side in a single library together with other library
facilities that are not Applications and are not covered by this
License, and convey such a combined library under terms of your
choice, if you do both of the following:

   a) Accompany the combined library with a copy of the same work based
   on the Library, uncombined with any other library facilities,
   conveyed under the terms of this License.

   b) Give prominent notice with the combined library that part of it
   is a work based on the Library, and explaining where to find the
   accompanying uncombined form of the same work.

  6. Revised Versions of the GNU Lesser General Public License.

  The Free Software Foundation may publish revised and/or new versions
of the GNU Lesser General Public License from time to time. Such new
versions will be similar in spirit to the present version, but may
differ in detail to address new problems or concerns.

  Each version is given a distinguishing version number. If the
Library as you received it specifies that a certain numbered version
of the GNU Lesser General Public License "or any later version"
applies to it, you have the option of following the terms and
conditions either of that published version or of any later version
published by the Free Software Foundation. If the Library as you
received it does not specify a version number of the GNU Lesser
General Public License, you may choose any version of the GNU Lesser
General Public License ever published by the Free Software Foundation.

  If the Library as you received it specifies that a proxy can decide
whether future versions of the GNU Lesser General Public License shall
apply, that proxy's public statement of acceptance of any version is
permanent authorization for you to choose that version for the
Library.```

### PolyForm Shield License 1.0.0 (Sodium)
```
# PolyForm Shield License 1.0.0

<https://polyformproject.org/licenses/shield/1.0.0>

## Acceptance

In order to get any license under these terms, you must agree
to them as both strict obligations and conditions to all
your licenses.

## Copyright License

The licensor grants you a copyright license for the
software to do everything you might do with the software
that would otherwise infringe the licensor's copyright
in it for any permitted purpose.  However, you may
only distribute the software according to [Distribution
License](#distribution-license) and make changes or new works
based on the software according to [Changes and New Works
License](#changes-and-new-works-license).

## Distribution License

The licensor grants you an additional copyright license
to distribute copies of the software.  Your license
to distribute covers distributing the software with
changes and new works permitted by [Changes and New Works
License](#changes-and-new-works-license).

## Notices

You must ensure that anyone who gets a copy of any part of
the software from you also gets a copy of these terms or the
URL for them above, as well as copies of any plain-text lines
beginning with `Required Notice:` that the licensor provided
with the software.  For example:

> Required Notice: Copyright Yoyodyne, Inc. (http://example.com)

## Changes and New Works License

The licensor grants you an additional copyright license to
make changes and new works based on the software for any
permitted purpose.

## Patent License

The licensor grants you a patent license for the software that
covers patent claims the licensor can license, or becomes able
to license, that you would infringe by using the software.

## Noncompete

Any purpose is a permitted purpose, except for providing any
product that competes with the software or any product the
licensor or any of its affiliates provides using the software.

## Competition

Goods and services compete even when they provide functionality
through different kinds of interfaces or for different technical
platforms.  Applications can compete with services, libraries
with plugins, frameworks with development tools, and so on,
even if they're written in different programming languages
or for different computer architectures.  Goods and services
compete even when provided free of charge.  If you market a
product as a practical substitute for the software or another
product, it definitely competes.

## New Products

If you are using the software to provide a product that does
not compete, but the licensor or any of its affiliates brings
your product into competition by providing a new version of
the software or another product using the software, you may
continue using versions of the software available under these
terms beforehand to provide your competing product, but not
any later versions.

## Discontinued Products

You may begin using the software to compete with a product
or service that the licensor or any of its affiliates has
stopped providing, unless the licensor includes a plain-text
line beginning with `Licensor Line of Business:` with the
software that mentions that line of business.  For example:

> Licensor Line of Business: YoyodyneCMS Content Management
System (http://example.com/cms)

## Sales of Business

If the licensor or any of its affiliates sells a line of
business developing the software or using the software
to provide a product, the buyer can also enforce
[Noncompete](#noncompete) for that product.

## Fair Use

You may have "fair use" rights for the software under the
law. These terms do not limit them.

## No Other Rights

These terms do not allow you to sublicense or transfer any of
your licenses to anyone else, or prevent the licensor from
granting licenses to anyone else.  These terms do not imply
any other licenses.

## Patent Defense

If you make any written claim that the software infringes or
contributes to infringement of any patent, your patent license
for the software granted under these terms ends immediately. If
your company makes such a claim, your patent license ends
immediately for work on behalf of your company.

## Violations

The first time you are notified in writing that you have
violated any of these terms, or done anything with the software
not covered by your licenses, your licenses can nonetheless
continue if you come into full compliance with these terms,
and take practical steps to correct past violations, within
32 days of receiving notice.  Otherwise, all your licenses
end immediately.

## No Liability

***As far as the law allows, the software comes as is, without
any warranty or condition, and the licensor will not be liable
to you for any damages arising out of these terms or the use
or nature of the software, under any kind of legal claim.***

## Definitions

The **licensor** is the individual or entity offering these
terms, and the **software** is the software the licensor makes
available under these terms.

A **product** can be a good or service, or a combination
of them.

**You** refers to the individual or entity agreeing to these
terms.

**Your company** is any legal entity, sole proprietorship,
or other kind of organization that you work for, plus all
its affiliates.

**Affiliates** means the other organizations than an
organization has control over, is under the control of, or is
under common control with.

**Control** means ownership of substantially all the assets of
an entity, or the power to direct its management and policies
by vote, contract, or otherwise.  Control can be direct or
indirect.

**Your licenses** are all the licenses granted to you for the
software under these terms.

**Use** means anything you do with the software requiring one
of your licenses.
```

## Distant Horizons

Leonida bundles Distant Horizons 3.3.4 for Minecraft 26.3 (Fabric) unmodified, as a nested jar, so the whole map
shows as level-of-detail terrain from the air.

- Project: https://modrinth.com/mod/distanthorizons and https://gitlab.com/distant-horizons-team/distant-horizons
- Licence: GNU Lesser General Public License v3.0 only (LGPL-3.0-only), see the LGPL-3.0 text in this notice
  set (the same text that covers Iris) and LICENSE.txt inside the nested jar. Source code: the GitLab link above.
- The nested jar also carries its own bundled libraries with their licence files, and the Distant Horizons logos,
  which are CC BY-SA 4.0 by Pankakes (LICENSE-LOGOS.txt inside the nested jar). Leonida does not modify them.
- Leonida only writes first launch defaults (LOD radius 128 chunks, LOW quality preset, LOW_IMPACT thread
  preset) through Distant Horizons' own config, once. Players can change them in its options screen.
