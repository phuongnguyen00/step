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
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

public final class FindMeetingQuery {
  public Collection<TimeRange> query(Collection<Event> events, MeetingRequest request) {
    // Assume that one person does not have two meetings at the same time
    
    //Step 0: Process the raw information
    int meetingDuration = (int) request.getDuration();
    Collection<String> mandatoryAttendees = request.getAttendees();
    Collection<String> optionalAttendees = request.getOptionalAttendees();
    ArrayList<TimeRange> availableSlots = new ArrayList<TimeRange>();

    // If no attendees, then the whole day is available for the request (assume duration <= 1 day)
    if (mandatoryAttendees.isEmpty() && optionalAttendees.isEmpty()){
        availableSlots.add(TimeRange.WHOLE_DAY);
        return availableSlots;
        
    } else { // There are some attendees, either optional or mandatory ones
        // Step 1: Store the information of the mandatory attenddees provided in events

        // commonCalendar is a lookup table where each person's name is the key
        // and the associated values are the times in the day when they are busy
        HashMap<String, ArrayList<TimeRange>> commonCalendar = createCommonCalendar(events);

        // Step 2: Return calendars based on whether there are only mandatory attendees, optional attendees, or both
        if (!mandatoryAttendees.isEmpty() && optionalAttendees.isEmpty()) {
            ArrayList<TimeRange> allAvailableSlots = getFreeTimeSlots(commonCalendar, mandatoryAttendees);
            availableSlots = TimeRange.getRangesLongEnough(allAvailableSlots, meetingDuration);
            return availableSlots;
        
        } else if (mandatoryAttendees.isEmpty() && !optionalAttendees.isEmpty()) {

            ArrayList<TimeRange> availableOptional = getFreeTimeSlots(commonCalendar, optionalAttendees);
            ArrayList<TimeRange> availableOptionalOnly = TimeRange.getRangesLongEnough(availableOptional, meetingDuration);
            return availableOptionalOnly;

        } else { // Both mandatory and optional attendees are present
            // Get the slots that work for optional attendees and check if any of them overlap with 
            // available slots for mandatory attendees. If yes, then return available slots. 

            ArrayList<TimeRange> allAvailableSlots = getFreeTimeSlots(commonCalendar, mandatoryAttendees);
            availableSlots = TimeRange.getRangesLongEnough(allAvailableSlots, meetingDuration);

            // Do not check long enough ranges because duration is checked when finding the intersection
            ArrayList<TimeRange> availableOptional = getFreeTimeSlots(commonCalendar, optionalAttendees);

            ArrayList<TimeRange> availableWithOptional = getIntersection(availableSlots, availableOptional, meetingDuration);

            // If there are some slots that work for all mandatory and optional attendees, then return 
            if (!availableWithOptional.isEmpty()) return TimeRange.getUniqueSortedSlots(availableWithOptional);

            // Otherwise, just return the slots that work for mandatory attendees
            return availableSlots;
        }
    }
  }
  
  /**
  * Create a common calendar that stores all occcupied time slots of people from a collection of events
  */
  private HashMap<String, ArrayList<TimeRange>> createCommonCalendar(Collection<Event> events) {
    HashMap<String, ArrayList<TimeRange>> commonCalendar = new HashMap<String, ArrayList<TimeRange>>(); 

    for (Event event: events) {
        for (String attendee: event.getAttendees()){
            if (commonCalendar.containsKey(attendee)) {
                commonCalendar.get(attendee).add(event.getWhen());
            } else {
                ArrayList<TimeRange> occupiedSlots = new ArrayList<TimeRange>();
                occupiedSlots.add(event.getWhen());
                commonCalendar.put(attendee, occupiedSlots);
            }
        }
    }

    return commonCalendar;
  }

  /**
  * Given a calendar, a list of attendees and meeting duration, return a list of all free time slots
  * Duration is not taken into account
  */
  private ArrayList<TimeRange> getFreeTimeSlots(HashMap<String, ArrayList<TimeRange>> commonCalendar, Collection<String> attendees){
    // Step 1: Find all occupied slots of relevent people based on the list of attendees
    ArrayList<TimeRange> allOccupiedSlots = getAllOccupiedSlots(commonCalendar, attendees);

    // Step 2: Find all possible windows by merging occupied ones and fine the (inverse selection) of those times
    // Step 2a: Sort the allOccupiedSlots
    Collections.sort(allOccupiedSlots, TimeRange.ORDER_BY_START);

    // Step 2b: Get a concise version of all occupied slots (merge overlapping ones)
    allOccupiedSlots = getOverlappedSlots(allOccupiedSlots);

    // Step 2c: Get the available slots
    ArrayList<TimeRange> allAvailableSlots = getInverseSlots(allOccupiedSlots);
    return allAvailableSlots;
  }

  /**
  * @return an arrayList of all occupied slots from a common calendar of attendees
  */
  private ArrayList<TimeRange> getAllOccupiedSlots(HashMap<String, ArrayList<TimeRange>> calendar, Collection<String> attendees){
    ArrayList<TimeRange> allOccupiedSlots = new ArrayList<TimeRange>();
    for (String attendee: attendees){
        if (calendar.containsKey(attendee)) {
            ArrayList<TimeRange> occupiedSlots = calendar.get(attendee);
            for (int i = 0; i < occupiedSlots.size(); i++) {
                allOccupiedSlots.add(occupiedSlots.get(i));
            }
        } else {// The attendee is not in the database yet, which means this attendee is assumed to be free all day
            allOccupiedSlots.add(TimeRange.fromStartEnd(0, 0, false));
        } 
    }
    return allOccupiedSlots;
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

      if (timeSlots.size() == 1) {
          overlappedSlots.add(overlappedSlot);
          return overlappedSlots;
      }
      
      boolean inclusive = false;
      // There are at least two things in timeSlots
      for (int i = 1; i < timeSlots.size(); i++){

          if (timeSlots.get(i).overlaps(overlappedSlot)){
              if (timeSlots.get(i).end() == TimeRange.END_OF_DAY) {inclusive = true;}
              overlappedSlot = overlappedSlot.mergeOverlapped(timeSlots.get(i), inclusive);
              if (i == timeSlots.size() - 1) {// If this is the last time slot
                    overlappedSlots.add(overlappedSlot);
              }
          } else {
              overlappedSlots.add(overlappedSlot);
              overlappedSlot = timeSlots.get(i);
              if (i == timeSlots.size() - 1) {
                  overlappedSlots.add(overlappedSlot);
              }
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

       // All elements in timeSlots do not overlap
       for (int i = 0; i < timeSlots.size() - 1; i++) {
           TimeRange otherSlot = TimeRange.fromStartEnd(timeSlots.get(i).end(), timeSlots.get(i+1).start(), false);
           otherSlots.add(otherSlot);
       }
    
       // The order is reserved
       if (timeSlots.get(timeSlots.size()-1).end() < TimeRange.END_OF_DAY) {
           otherSlots.add(TimeRange.fromStartEnd(timeSlots.get(timeSlots.size()-1).end(), TimeRange.END_OF_DAY, true));
       }

       return otherSlots;
  }
  
  
  /**
  * @return a list of intersecting time ranges with primary slots as the starting point. The returned time ranges must satisfy the duration.
  */
  private ArrayList<TimeRange> getIntersection(ArrayList<TimeRange> primarySlots, ArrayList<TimeRange> optionalSlots, int duration) {
      ArrayList<TimeRange> intersectionSlots = new ArrayList<TimeRange>();

      for (int i = 0; i < primarySlots.size(); i++){
        for (int j = 0; j < optionalSlots.size(); j++) {
            TimeRange primary = primarySlots.get(i);
            TimeRange optional = optionalSlots.get(j);

            // If there is no overlap, then doesn't care about optional available slots
            if (primary.overlaps(optional)) {
                if (optional.contains(primary)) {
                    intersectionSlots.add(primary);

                } else if (primary.contains(optional) && !primary.equals(optional)) {
                    if (optional.hasEnoughTime(duration)) {intersectionSlots.add(optional);}

                } else { // Two slots overlap but one does not contain another
                    TimeRange newSlot = primary.getIntersection(optional);
                    if (newSlot.hasEnoughTime(duration)) {intersectionSlots.add(newSlot);}
                }

            // If there is no current overlap with the optional slot and no future overlaps, 
            // then no overlap with next optional slots in this iteration
            } else {
                if (primary.start() < optional.start()) {break;} 
                //There maybe future overlaps, so skip this iteration
                else {continue;}
            }
        }
    }

    return intersectionSlots;
  }

}
