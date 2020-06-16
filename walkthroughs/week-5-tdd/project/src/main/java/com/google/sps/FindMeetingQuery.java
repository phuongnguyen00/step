// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.sps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

public final class FindMeetingQuery {
  public Collection<TimeRange> query(Collection<Event> events, MeetingRequest request) {
    throw new UnsupportedOperationException();

    // Assume that one person does not have two meetings at the same time

    // CalendarAttendees is a lookup table where each person's name is the key
    // and the associated values are the times in the day when they are busy
    HashMap<String, SortedSet<TimeRange>> calendarAttendees = new HashMap<String, SortedSet<TimeRange>>(); 

    // Step 1: Store the information of the attenddees provided in events
    for (Event event: events) {
        for (String attendee: event.getAttendees()){
            if calendarAttenddees.containsKey(attendee){
                calendarAttenddees.get(attendee).add(event.getWhen());
            } else {
                SortedSet<TimeRange> occupiedSlots = new TreeSet<TimeRange>(TimeRange.ORDER_BY_START);
                occupiedSlots.add(event.getWhen());
                calendarAttenddees.put(attendee, occupiedSlots);
            }
        }
    }


    for (String attenddee: request.getAttendees()){
        SortedSet<TimeRange> occupiedSlots = calendarAttendees.get(attendee);
    }


    

  }
  
  /**
  * @param a TreeSet of timeslots, can be occupied or unoccupied
  * @return all overlap time slots in a tree set
  */
  private TreeSet<TimeRange> getOverlappedSlots(TreeSet<TimeRange> timeSlots) {
      TreeSet<TimeRange> overlappedSlots = new TreeSet<TimeRange>(TimeRange.ORDER_BY_START);
      if (timeSlots.isEmpty()){
          return overlappedSlots;
      }

      // Start from the first time slot, return the most concise treeset of time slots (that are either occupied or not)
      TimeRange overlappedSlot = timeSlots.first();
      for (TimeRange slot: timeSlots){
          if (overlappedSlot.overlap(slot)){
              overlappedSlot = overlappedSlot.mergeOverlapped(slot)
          } else {
              overlappedSlots.add(overlappedSlot);
              overlappedSlot = slot;
          }
      }

      return overlappedSlots;
  }

  /**
  * @param timeSlots a treeSet of TimeRanges
  * @return the other slots in a day
  */
  private TreeSet<TimeRange> getInverseSlots(TreeSet<TimeRange> timeSlots){
       TreeSet<TimeRange> otherSlots = new TreeSet<TimeRange>(TimeRange.ORDER_BY_START);
       
  }
}
