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
import java.util.HashMap;
import java.util.HashSet;

public final class FindMeetingQuery {
  public Collection<TimeRange> query(Collection<Event> events, MeetingRequest request) {

    // Assume that one person does not have two meetings at the same time

    // CalendarAttendees is a lookup table where each person's name is the key
    // and the associated values are the times in the day when they are busy
    HashMap<String, ArrayList<TimeRange>> calendarAttendees = new HashMap<String, ArrayList<TimeRange>>(); 

    // Step 1: Store the information of the attenddees provided in events
    for (Event event: events) {
        for (String attendee: event.getAttendees()){
            if (calendarAttendees.containsKey(attendee)) {
                calendarAttendees.get(attendee).add(event.getWhen());
            } else {
                ArrayList<TimeRange> occupiedSlots = new ArrayList<TimeRange>();
                occupiedSlots.add(event.getWhen());
                calendarAttendees.put(attendee, occupiedSlots);
            }
        }
    }

    // Step 2: Find all possible free windows
    ArrayList<TimeRange> allOccupiedSlots = new ArrayList<TimeRange>();
    for (String attendee: request.getAttendees()){
        ArrayList<TimeRange> occupiedSlots = calendarAttendees.get(attendee);
        for (int i = 0; i < occupiedSlots.size(); i++) {
            allOccupiedSlots.add(occupiedSlots.get(i));
        }
    }
    Collections.sort(allOccupiedSlots, TimeRange.ORDER_BY_START);
    allOccupiedSlots = getOverlappedSlots(allOccupiedSlots);
    ArrayList<TimeRange> allAvailableSlots = getInverseSlots(allOccupiedSlots);
    ArrayList<TimeRange> availableSlots = new ArrayList<TimeRange>();

    for (int i = 0; i < allAvailableSlots.size(); i++) {
        if (allAvailableSlots.get(i).duration() >= request.getDuration()) {
            availableSlots.add(allAvailableSlots.get(i));
        }
    }
    return availableSlots;
  }
  
  /**
  * @param an ArrayList of timeslots sorted by start, can be occupied or unoccupied
  * @return all overlap time slots 
  */
  private ArrayList<TimeRange> getOverlappedSlots(ArrayList<TimeRange> timeSlots) {
      ArrayList<TimeRange> overlappedSlots = new ArrayList<TimeRange>();
      if (timeSlots.isEmpty()){
          return overlappedSlots;
      }

      // Start from the first time slot, return the most concise treeset of time slots (that are either occupied or not)
      TimeRange overlappedSlot = timeSlots.get(0);

      for (int i = 1; i < overlappedSlots.size(); i++){
          if (overlappedSlots.get(i).overlaps(overlappedSlot)){
              overlappedSlot = overlappedSlot.mergeOverlapped(overlappedSlots.get(i));
          } else {
              overlappedSlots.add(overlappedSlot);
              overlappedSlot = overlappedSlots.get(i);
          }
      }

      return overlappedSlots;
  }

  /**
  * @param timeSlots an arrayList of TimeRanges that are sorted whose elements do not overlap (has gone through getOverlappedSlots)
  * @return the other slots in a day
  */
  private ArrayList<TimeRange> getInverseSlots(ArrayList<TimeRange> timeSlots){
       ArrayList<TimeRange> otherSlots = new ArrayList<TimeRange>();
       if (timeSlots.isEmpty()) {return otherSlots;}

       // Assume that timeSlots only get the most concise slots, and the last slot also ends the latest
    
       if (timeSlots.get(0).start() > TimeRange.START_OF_DAY) {
           otherSlots.add(TimeRange.fromStartEnd(TimeRange.START_OF_DAY, timeSlots.get(0).start(), false));
       }

       if (timeSlots.get(timeSlots.size()-1).end() < TimeRange.END_OF_DAY) {
           otherSlots.add(TimeRange.fromStartEnd(timeSlots.get(0).end(), TimeRange.END_OF_DAY, true));
       }

       if (timeSlots.size() == 1) {
           return otherSlots;
           System.out.println("I come into this slot");
       }

       // All elements in timeSlots do not overlap
       for (int i = 0; i < timeSlots.size() - 1; i++) {
           TimeRange otherSlot = TimeRange.fromStartEnd(timeSlots.get(i).end(), timeSlots.get(i+1).start(), true);
           otherSlots.add(otherSlot);
       }

       return otherSlots;
  }
}
