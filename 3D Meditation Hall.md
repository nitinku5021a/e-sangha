# 3D Meditation Hall — Implementation Instruction

Build a **3D virtual meditation hall screen** for the Android Vipassana meditation app.

The purpose of this screen is to create the feeling that the user is **sitting together with other meditators in the same physical meditation hall**, without turning the experience into a game or social-media experience.

## 1. Core Experience

The user should see a peaceful, realistic 3D meditation hall from a fixed, slightly elevated perspective.

The scene should contain:

- A simple meditation hall interior
- Front altar / Buddha / meditation focal point
- Meditation cushions arranged in rows
- Seated meditator representations
- The user's own meditation position
- Subtle environmental lighting
- A minimal meditation timer / session status

The overall feeling should be:

> "I am sitting here, in this hall, with other people who are meditating with me."

It should **not** feel like a video game.

---

## 2. Visual Style

Use a calm, minimal and contemplative aesthetic.

Avoid:

- Cartoonish graphics
- Game-like avatars
- Excessive animation
- Bright colors
- Floating UI elements
- Camera movement
- Gamification
- Chat bubbles
- Reactions
- Leaderboards
- Social-media-like elements

Prefer:

- Natural materials
- Wood
- Warm, soft lighting
- Simple architecture
- Neutral colors
- Minimal decoration
- Realistic or highly polished stylized 3D assets
- Large amounts of visual breathing space

The 3D environment should be beautiful but **visually quiet**.

---

## 3. Camera

Use a fixed camera.

Recommended perspective:

- Slightly elevated
- Looking toward the front of the meditation hall
- Mild perspective rather than an orthographic "game board" appearance
- Camera should remain stationary during meditation

Do not continuously rotate or move the camera.

If camera movement is implemented later, it should be extremely slow and optional.

---

## 4. Hall Layout

Create a physical hall approximately like:

```text
                 FRONT / ALTAR

          ┌─────────────────────┐
          │      🕯 ALTAR       │
          │                     │
          │   ●  ●  ●  ●  ●    │
          │                     │
          │   ●  ●  ●  ●  ●    │
          │                     │
          │   ●  ●  ●  ●  ●    │
          │                     │
          │   ●  ●  ●  ●  ●    │
          │                     │
          │        YOU          │
          └─────────────────────┘

                  ENTRANCE
```

The actual arrangement should be determined by the 3D perspective.

Meditation positions should be predefined.

Each position should have a unique identifier.

Example:

```text
seat_01
seat_02
seat_03
...
seat_50
```

The architecture should allow the number of seats to scale without requiring a new 3D environment.

---

## 5. Meditators

Do NOT create complex animated human avatars.

Use a simple seated meditation representation.

For example:

- Seated human silhouette
- Simple monk-like silhouette
- Minimal 3D human model
- No detailed facial features
- No unnecessary clothing details

The representation should communicate:

**"Someone is sitting in meditation."**

It should not communicate:

**"Here is a game character."**

Create one high-quality seated meditator model and reuse it for multiple participants through instancing where possible.

---

## 6. User Representation

The current user should occupy one meditation position.

Their representation can be subtly distinguishable from other meditators.

Possible methods:

- Slightly different material
- Very subtle light
- Small halo/outline
- Slightly different seating cushion
- Minimal indicator

Do NOT use an aggressive selection highlight.

The distinction should be subtle.

The user should still visually feel like **one meditator among many**.

---

## 7. Live Group Sitting

The hall represents the **actual current meditation session**.

If 6 people are currently sitting:

```text
🧘 🧘 🧘

🧘 🧘

    🧘
```

show six meditators.

If another person joins:

```text
Before: 6 meditators

After: 7 meditators
```

The new meditator should appear at an available meditation position.

If someone leaves, their position becomes empty.

Joining and leaving should use subtle transitions rather than abrupt visual changes.

For example:

- Fade in when joining
- Fade out when leaving

Do not make people walk into the room.

---

## 8. Animation

Animation should be extremely subtle.

The primary possible animation is breathing.

For example:

- Tiny vertical movement
- Very subtle torso movement
- Slight breathing rhythm

The movement should be barely noticeable.

The user should not consciously focus on the animation.

Avoid:

- Walking
- Hand gestures
- Head movements
- Talking
- Looking around
- Emotes
- Large body movements
- Dancing
- Game-like idle animations

The purpose of animation is only to make the hall feel alive.

---

## 9. Meditation Timer

The timer should remain visually subordinate to the 3D environment.

Example:

```text
             47:32

        [3D MEDITATION HALL]

          12 people sitting
```

The timer should be minimal.

Do not cover a large portion of the 3D scene with UI.

The user should primarily experience the hall.

---

## 10. Session Information

Show only essential information.

Potential information:

- Remaining time
- Number of meditators currently sitting
- Sitting/session name
- Very minimal session status

Example:

```text
        Morning Sitting

             47:32

        12 meditators
```

Avoid adding unnecessary controls.

---

## 11. User Interaction

The default interaction should be extremely simple.

The user should primarily:

1. Enter the meditation session
2. See the hall
3. See other meditators
4. Start meditation
5. Meditate

Do not require the user to interact with the 3D environment.

Optional future functionality could include:

- Selecting a meditation cushion
- Choosing a preferred position
- Very gentle camera repositioning
- Looking around the hall

But these should NOT be required for the MVP.

---

# 12. Recommended Technology

For a native Android application, investigate using:

**Google Filament**

for rendering the 3D environment.

Use:

- GLTF / GLB assets
- Filament for rendering
- Android native integration
- Jetpack Compose for surrounding UI if the existing application uses Compose

Do NOT introduce Unity unless there is a compelling technical reason.

The goal is to keep the meditation hall lightweight and tightly integrated with the Android application.

---

# 13. Architecture

Keep the 3D layer independent from the meditation/session logic.

Recommended conceptual architecture:

```text
Android Application
│
├── Meditation UI
│
├── Meditation Timer
│
├── Meditation Session
│     │
│     ├── Session ID
│     ├── Participants
│     ├── Start time
│     ├── End time
│     └── Current user
│
└── Meditation Hall Renderer
      │
      ├── Hall Model
      ├── Seat Positions
      ├── Meditator Instances
      ├── User Representation
      ├── Lighting
      └── Animation
```

The renderer should receive a simple state representation such as:

```text
MeditationHallState

sessionId
currentUserId
participants[]
availableSeats[]
```

Each participant should contain something equivalent to:

```text
participantId
seatId
isCurrentUser
```

The renderer should then translate that state into the 3D scene.

---

# 14. Seat Management

The 3D scene should not determine who sits where.

The application/session layer should determine seat allocation.

For example:

```text
Participant A → seat_07
Participant B → seat_12
Participant C → seat_03
```

The renderer simply displays them.

This separation is important because the same session state may eventually need to be represented in:

- 3D hall
- 2D fallback
- Accessibility UI
- Web client
- Other platforms

---

# 15. Performance Requirements

The 3D hall must work smoothly on ordinary Android phones.

Target:

- Stable 60 FPS on capable devices
- Graceful performance on lower-end devices
- Low battery consumption
- Low memory usage
- Fast initial loading
- No unnecessary network traffic for rendering

Optimize the scene using:

- Instancing
- Low/moderate polygon count
- Texture compression
- Appropriate LOD
- Baked lighting where practical
- Minimal dynamic lights
- Efficient GLTF/GLB assets

Do not build a visually impressive scene that unnecessarily consumes large amounts of CPU/GPU/battery.

---

# 16. Offline/Fallback Behavior

The meditation hall should ideally work even with poor network connectivity once its assets have been downloaded.

The 3D environment itself should be locally packaged or cached.

Network connectivity should primarily be required for:

- Joining a live session
- Receiving participant updates
- Synchronizing session state

If 3D rendering is unavailable on a particular device, provide a graceful fallback rather than preventing meditation.

Possible fallback:

```text
2D meditation hall
+
simple seated meditator icons
```

The meditation timer and sitting functionality must continue working.

---

# 17. Important Product Principle

The 3D hall is **not the product itself**.

Meditation is the product.

The 3D hall exists only to create a stronger sense of:

> **Presence + Togetherness + Shared Practice**

Therefore every visual and technical decision should prioritize:

1. Peacefulness
2. Simplicity
3. Presence
4. Low distraction
5. Performance
6. Reliability

over visual complexity.

---

# 18. MVP Scope

For the first implementation, build ONLY:

### Environment

- One meditation hall
- One fixed camera
- One lighting setup
- One altar
- Fixed meditation seats
- One seated meditator model

### Functionality

- Display empty seats
- Display occupied seats
- Display current user
- Join/leave participant transitions
- Subtle breathing animation
- Meditation timer overlay
- Current participant count

### Do NOT implement yet

- Multiple halls
- Avatar customization
- Walking
- Voice
- Chat
- Reactions
- Camera controls
- Gamification
- Social profiles
- Virtual goods
- Achievements
- Complex physics
- Multiplayer 3D networking

Keep the first version extremely focused.

---

# 19. Deliverables

Implement the feature as a production-quality Android component.

Provide:

1. 3D meditation hall asset structure
2. Filament rendering component
3. Seat-position system
4. Meditator instancing system
5. Current-user representation
6. Participant state → 3D scene synchronization
7. Join/leave transitions
8. Subtle breathing animation
9. Timer overlay integration
10. Performance optimization
11. Low-end-device fallback
12. Clean separation between session state and rendering

The resulting experience should feel like entering a **quiet physical meditation hall**, not launching a 3D game.