/*
    Copyright (C) 2017 Aseman Team
    http://aseman.co

    Meikade is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Meikade is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

import QtQuick 2.0
import AsemanTools 1.0
import AsemanTools.Awesome 1.0

Item {
    id: category
    width: 100
    height: 62

    property int catId: -1
    property alias itemsSpacing: category_list.spacing
    property real topMargin: itemsSpacing
    property alias header: category_list.header
    property alias footer: category_list.footer

    property alias list: category_list

    onCatIdChanged: category_list.refresh()

    signal categorySelected( int cid, variant rect )
    signal poemSelected( int pid, variant rect )

    Connections {
        target: Database
        onPoetsChanged: category_list.refresh()
    }

    AsemanListView {
        id: category_list
        anchors.fill: parent
        bottomMargin: View.navigationBarHeight + spacing
        clip: true
        spacing: 8*Devices.density
        topMargin: category.topMargin
        model: ListModel {}
        delegate: Rectangle {
            id: item
            x: category_list.spacing
            width: category_list.width - 2*x
            height: 55*Devices.density
            border.width: 1*Devices.density
            border.color: Meikade.nightTheme? "#333333" : "#cccccc"
            color: marea.pressed? (Meikade.nightTheme? "#1D2124" : "#CFDAFF") : (Meikade.nightTheme? "#111111" : "#ffffff")

            CategoryItem {
                anchors.fill: parent
                cid: identifier
                root: category.catId == 0
            }

            Text {
                id: go_img
                anchors.right: View.defaultLayout? parent.right : undefined
                anchors.left: View.defaultLayout? undefined : parent.left
                anchors.verticalCenter: parent.verticalCenter
                anchors.margins: 12*Devices.density
                font.pixelSize: 30*globalFontDensity*Devices.fontDensity
                font.family: Awesome.family
                color: "#44000000"
                text: View.defaultLayout? Awesome.fa_angle_right : Awesome.fa_angle_left
            }

            MouseArea{
                id: marea
                anchors.fill: parent
                onClicked: {
                    var childs = Database.childsOf(identifier)

                    var map = item.mapToItem(category, 0, 0)
                    var rect = Qt.rect(map.x, map.y, item.width, item.height)
                    if( identifier == -1 ) {
                        category.poemSelected(category.catId, rect)
                    } else if( childs.length === 0 && !item.hafezOmen ) {
                        category.poemSelected(identifier, rect)
                    } else {
                        category.categorySelected(identifier, rect)
                    }

                    if(catId == 0)
                        networkFeatures.pushAction( ("Poet Selected: %1").arg(identifier) )
                    else
                        networkFeatures.pushAction( ("Cat Selected: %1").arg(identifier) )
                }
            }
        }

        function refresh() {
            model.clear()

            var list = category.catId==0? Database.poets() : Database.childsOf(category.catId)
            for( var i=0; i<list.length; i++ ) {
                model.append({"identifier":list[i]})
            }

            var poems = Database.catPoems(category.catId)
            if(poems.length != 0)
                model.append({"identifier": -1})

            focus = true
        }
    }

    ScrollBar {
        scrollArea: category_list; height: category_list.height-topMargin-View.navigationBarHeight
        anchors.right: category_list.right; anchors.top: category_list.top
        anchors.topMargin: topMargin; color: Meikade.nightTheme? "#ffffff" : "#881010"
        LayoutMirroring.enabled: View.layoutDirection == Qt.RightToLeft
    }

    CategoryEmptyArea {
        anchors.fill: parent
        visible: category_list.count == 0
    }
}
