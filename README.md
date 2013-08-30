# phytomap

A ClojureScript app designed display (statistical) data from the freifunk (kbu) mesh node network.

## Use Cases

The following audiences are being targed by phytomap:

* WLAN-Seeking Persons do want to have connection to the Freifunk network. To archieve this goal, they have to find a working, nearby node. To support this use case, a list of working nodes sorted by distance to the person's location with just basic info about the node plus a map where the node can be found is shown.
* Power users need a more info and sorting options. Thus, a list of complete info including several sorting options is shown.

## To Do
* Make runnable on mobile/iOS devices (BUG: shows too few nodes when in simple mode)
* Use core.async for concurrent network accesses and the geolocator
* Make the height of table and the map so that they fill the bottom of the visible area only

## Usage

FIXME

## License

Copyright © 2013 Axel Katerbau, Dirk Theisen

Distributed under the Eclipse Public License, the same as Clojure.
