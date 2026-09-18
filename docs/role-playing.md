# Role Playing

Purpose: my role-playing game teaches participants how "read models" and "processors" work in event-sourcing by having them keep track of events relevant to the read model or processor that they are "role playing", updating the information on their personal table (for read models) and triggering commands (for processors). The "event log" is visible to everyone, and each event is "sent" to everyone when the event log commits the event and assigns it a "sequence number".

Domain context: the domain used is the "jitterticket" concert ticketing system, with entities "Concert", "Customer", and "Ticket". Concerts get scheduled, which causes tickets to become available for purchase.

The game starts with everyone simulating (role-playing) a single read model called "Available Concerts" which tracks the concerts for which tickets can be purchased (each participant has their own copy that they're working on, so that I can check that everyone has the same understanding). Initially I ask them for "hey, read models, give me the list of available concerts", to which they should all reply "empty list" (i.e., there are none—yet).

Using an Event-Modeling diagram to show the flow as a diagram, I then simulate filling out the UI for scheduling a new concert, which triggers a "Schedule Concert" command that succeeds and emits a "ConcertScheduled" event to be appended to the event log. The event log assigns a sequence number (`seq#`) and adds it to the event log display. At that point, everyone should be updating their read model to indicate that there's now a concert. At this point I can scan their read models in the Miro board, but don't ask them anything yet.

I then show the "reschedule concert" flow event-model diagram, which first requires showing all concerts obtained from the read models. I ask the read models for: concert ID, artist name, and show date-time. At this point, we "discover" that the read models might not have been "storing" all the information needed for this UI, or, perhaps they're storing _too much_ information, such as ticket price and doors time. We can then discuss what this particular read model is designed for.

After clarifying what's needed from the Read Models, we "display" the list of concerts on a UI and I simulate clicking the "reschedule" button next to that concert (embeds the concert ID in the request), which redirects to the "reschedule" UI containing the ID for that concert as well as the current information. I simulate entering a new date/time, which triggers the "Reschedule Concert" command, which emits the "ConcertRescheduled" event (we're assuming no validation problems), with the Concert ID and the new show date-time and new "doors open" time. This then is appended to the event log, and I inform the read models "hey, new event, with seq# 2" and they update their read models.

## Split Group for Multiple Read Models

At this point having checked that the participants understand how read models get updated, I assign specific read models to everyone (I don't assign all of them up-front, I may double-up folks, so that I can add ones that we "discover" that we need later):

1. Concerts for customers to see which ones they can buy tickets for
2. All concerts ever scheduled with sales information for each one: tickets sold and revenue
3. Dates (only) of concerts that haven't happened yet: used to ensure we don't have scheduling conflicts and double-book the venue
4. Tickets per customer: so a customer can view the tickets they bought
5. Tickets for a specific concert: so the "ticket taker" at the venue can validate the ticket and mark it as "used" after admitting the right number of people

I also want some "processors" that watch out for events, perhaps keeping some local state, and then they emit commands when a threshold is met or a state is reached

1. Sold-out Processor: tracks when the last ticket for a concert was sold and triggers a "Stop Ticket Sales" command.
2. 

## Lagging and Crashing Read Models

I want to simulate a Read Model that crashed, where I point to a person and say, "oops, you crashed," and lost everything you were keeping track of. They then have to create a "new" copy of the read model, catching up by looking at the event log from the beginning. I want a variant of this where they have to "persist" their read model to "disk" (write it somewhere else on the board), but that might take a bit of time, so they have to catch up on any missed events while they were busy. The ones that persist can recover from that persistence after they crash.

I also want to simulate a Read Model that is slow (to show consistency lag), but not sure how to do that: could use ideas here. Perhaps they have to count to 30 before updating their read model.

## Commands and Events

These are the commands with the events they emit and the information they contain:

1. Schedule Concert -> ConcertScheduled: ConcertId, Artist name, Show Date-Time, Doors Open Time, Capacity (how many tickets are available), Ticket Price, Max tickets per customer
2. Reschedule Concert -> ConcertRescheduled: ConcertId, new show date-time, new doors open time
3. Register Customer -> CustomerRegistered: CustomerId, email 
4. Purchase Ticket -> TicketsSold: ConcertId, CustomerId, TicketId, quantity tickets purchased, total cost
5. Stop Ticket Sales -> TicketSalesStopped: ConcertId, reason (string description)

Commands that we add later on, because a "new feature" is discovered, e.g., scheduling a concert does not immediately make tickets available. So, need a "tickets on sale" command:

6. Schedule Ticket Sales -> TicketSalesScheduled: ConcertId, ticket sales start date-time
7. Start Ticket Sales -> TicketSalesStarted: ConcertId, reason (string description, e.g., "scheduled time", or "manual ticket sales start")

With that new command, we'll also need another Processor to track that scheduled ticket sales should now start, i.e., it emits a Start Ticket Sales command.

## Idempotency

I want to demonstrate that if a Processor crashes, things are fine, e.g., after emitting a "Start Ticket Sales", it crashes, recovers, and re-triggers the "Start Ticket Sales", but because the command is idempotent, it does NOT issue a new "TicketSalesStarted" event.

Other idempotency demonstrations would be useful: ideas?

## Facilitation

Would be great to have a bunch of UIs, Commands, and Events "sticky notes" already generated that I could pull off from a pile somewhere on the Miro board so I'm not creating and typing notes on-the-fly.

What more do you need from me to create a Miro board that faciliates this role-playing game?
