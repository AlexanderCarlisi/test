package com.mygdx.game;

import java.util.HashMap;
import java.util.function.BooleanSupplier;

import com.badlogic.gdx.ai.steer.behaviors.Jump;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.physics.box2d.*;
import org.libsdl.SDL;
import org.libsdl.SDL_Error;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Vector2;

import uk.co.electronstudio.sdl2gdx.SDL2Controller;
import uk.co.electronstudio.sdl2gdx.SDL2ControllerManager;


public class PlayerController {

    /**
     * Key Binds Class
     */
    private static class ControlAction {
        private final BooleanSupplier condition;
        private final Runnable action;
    
        public ControlAction(BooleanSupplier condition, Runnable action) {
            this.condition = condition;
            this.action = action;
        }
    
        public void checkAndPerform() {
            if (condition.getAsBoolean()) {
                action.run();
            }
        }
    }


    public enum ControllerType {Keyboard, Keyboard2, Controller};
    private static final float MAX_VELOCITY_GROUNDED = 0.4f; // Should become character specific
    private static final float MAX_VELOCITY_AIRBORNE = 0.4f; // Should become character specific
    private static final long JUMP_DEBOUNCE = 125; // milliseconds
    private static final float AXIS_DEADZONE = 0.2f;
    private static final long GUARDBREAK_STUNTIME = 125;
    private static final float GUARD_DEGRADE = 0.25f;
    private static final float GUARD_GENERATE = 0.25f;
    private static final long GUARD_DEBOUNCE = 1000;

    private final Fighter m_fighter;
    private final HashMap<Fighter.Animations, Animation<TextureRegion>> m_animations;

    private final ControlAction[] m_bindings;
    private final ControllerType m_controllerType;
    private SDL2Controller m_controller;

    private boolean m_isGrounded;
    private boolean m_hasDoubleJump;
    private long m_lastJump;
    private boolean m_isFacingRight;
    private boolean m_isGuarding;

    private float m_previousY;
    private long m_previousTime;
    private long m_deltaTime;

    private float m_endLag;
    private long m_previousAttackTime;
    private float m_fallSpeed;
    private float m_guardPercent;
    private long m_previousGuardTime;
    private Animation<TextureRegion> m_currentAnimation;
    private Fighter.Animations m_currentAnimationEnum;
    private Fighter.Animations m_newAnimationEnum;
    private float m_stateTime;

    /**
     * Constructor for the Controller Class.
     * @param fighter 
     * @param controllerType
     */
    public PlayerController(Fighter fighter, ControllerType controllerType) {
        m_fighter = fighter;
        m_animations = m_fighter.getAnimations();
        m_isGrounded = true;
        m_hasDoubleJump = false;
        m_isFacingRight = false;
        m_previousY = 0;
        m_deltaTime = 0;
        m_previousTime = 0;
        m_endLag = 0;
        m_fallSpeed = 0;
        m_guardPercent = 100;
        m_controllerType = controllerType;
        m_stateTime = 0;
        m_currentAnimationEnum = Fighter.Animations.Idle;
        m_currentAnimation = m_animations.get(Fighter.Animations.Idle);

        // init bindings
        switch(m_controllerType) {
            case Keyboard:
                m_bindings = new ControlAction[] {
                        new ControlAction(() -> Gdx.input.isKeyPressed(Keys.A) && !Gdx.input.isKeyPressed(Keys.D), () -> moveXAxis(-1)),
                        new ControlAction(() -> Gdx.input.isKeyPressed(Keys.D) && !Gdx.input.isKeyPressed(Keys.A), () -> moveXAxis(1)),
                        new ControlAction(() -> Gdx.input.isKeyJustPressed(Keys.SPACE), this::jump),
                        new ControlAction(() -> Gdx.input.isKeyPressed(Keys.O), this::guard),
                        new ControlAction(() -> !Gdx.input.isKeyPressed(Keys.O), this::stopGuard),
                        new ControlAction(() -> Gdx.input.isKeyJustPressed(Keys.J), () -> attack(Attack.attackType.Basic)),
                        new ControlAction(() -> Gdx.input.isKeyJustPressed(Keys.K), () -> attack(Attack.attackType.Special)),
                        new ControlAction(() -> Gdx.input.isKeyJustPressed(Keys.L), () -> attack(Attack.attackType.Smash)),
                        new ControlAction(() -> Gdx.input.isKeyJustPressed(Keys.I), () -> attack(Attack.attackType.Ultimate))
                };
                break;

            case Keyboard2:
                m_bindings = new ControlAction[] {
                    new ControlAction(() -> Gdx.input.isKeyPressed(Keys.LEFT) && !Gdx.input.isKeyPressed(Keys.RIGHT), () -> moveXAxis(-1)),
                    new ControlAction(() -> Gdx.input.isKeyPressed(Keys.RIGHT) && !Gdx.input.isKeyPressed(Keys.LEFT), () -> moveXAxis(1)),
                    new ControlAction(() -> Gdx.input.isKeyJustPressed(Keys.UP), this::jump)
                };
                break;

            case Controller:
                try {
                    m_controller = new SDL2Controller(new SDL2ControllerManager(), 0);   
                } catch (SDL_Error e) {
                    m_controller = null;
                    m_bindings = new ControlAction[0];
                    return;
                }

                m_bindings = new ControlAction[] {
                        new ControlAction(() -> Math.abs(m_controller.getAxis(SDL.SDL_CONTROLLER_AXIS_LEFTX)) > AXIS_DEADZONE,
                                () -> moveXAxis(m_controller.getAxis(SDL.SDL_CONTROLLER_AXIS_LEFTX))),
                        new ControlAction(() -> m_controller.getButton(SDL.SDL_CONTROLLER_BUTTON_A), this::jump),
                        new ControlAction(() -> m_controller.getButton(SDL.SDL_CONTROLLER_BUTTON_X), () -> attack(Attack.attackType.Basic)),
                        new ControlAction(() -> m_controller.getButton(SDL.SDL_CONTROLLER_BUTTON_Y), () -> attack(Attack.attackType.Special)),
                        new ControlAction(() -> m_controller.getButton(SDL.SDL_CONTROLLER_BUTTON_B), () -> attack(Attack.attackType.Smash)),
                        new ControlAction(() -> m_controller.getButton(SDL.SDL_CONTROLLER_BUTTON_B) && m_controller.getButton(SDL.SDL_CONTROLLER_BUTTON_Y), () -> attack(Attack.attackType.Ultimate)),
                        new ControlAction(() -> m_controller.getButton(SDL.SDL_CONTROLLER_BUTTON_RIGHTSHOULDER), this::guard),
                        new ControlAction(() -> !m_controller.getButton(SDL.SDL_CONTROLLER_BUTTON_RIGHTSHOULDER), this::stopGuard),
                };
                break;

            default:
                m_bindings = new ControlAction[0];
                m_controller = null;
                break;
        }
    }


    /**
     * Move the Fighter on the X-Axis.
     * @param modifier value to modify the move direction by
     */
    public void moveXAxis(float modifier) {
        if (m_isGuarding) return;

        Body body = m_fighter.getBody();
        Vector2 pos = body.getPosition();
        Vector2 vel = body.getLinearVelocity();
        float maxVelocity = m_isGrounded ? MAX_VELOCITY_GROUNDED : MAX_VELOCITY_AIRBORNE;
        float signum = Math.signum(modifier);

        if ((signum == 1 && vel.x < maxVelocity) || (signum == -1 && vel.x > -maxVelocity)) {
            body.applyLinearImpulse(m_fighter.getRunSpeed() * modifier, 0, pos.x, pos.y, true);
        }

        m_isFacingRight = Math.signum(modifier) >= 0;
        m_newAnimationEnum = Fighter.Animations.Run;
    }


    public void jump() {
        if (m_isGuarding) return;

        Body body = m_fighter.getBody();
        Vector2 pos = body.getPosition();
        if (m_isGrounded && System.currentTimeMillis() - m_lastJump > JUMP_DEBOUNCE) {
            body.applyLinearImpulse(0, m_fighter.getJumpForce(), pos.x, pos.y, true);
            m_lastJump = System.currentTimeMillis();
            m_isGrounded = false;
        }
        else if (!m_isGrounded && m_hasDoubleJump && System.currentTimeMillis() - m_lastJump > JUMP_DEBOUNCE) {
            // body.applyLinearImpulse(0, m_fighter.getJumpForce() * (m_isFalling ? 3f : 1.35f), pos.x, pos.y, true);
            body.applyLinearImpulse(0, m_fighter.getJumpForce() * ((m_fallSpeed < 0) ? m_fallSpeed * -1.5f : 1), pos.x, pos.y, true);
            m_lastJump = System.currentTimeMillis();
            m_hasDoubleJump = false;
        }
        m_newAnimationEnum = Fighter.Animations.Jump;
    }


    private void guard() {
        if (m_guardPercent > 0 && System.currentTimeMillis() > m_previousGuardTime + GUARD_DEBOUNCE) {
            m_isGuarding = true;
        }
    }
    private void stopGuard() {
        // Prevent Spam
        if (System.currentTimeMillis() < m_previousGuardTime + GUARD_DEBOUNCE) return;

        m_isGuarding = false;
        m_previousGuardTime = System.currentTimeMillis();
    }


    public void attack(Attack.attackType attackType) {
        if (m_isGuarding) return;

        // Don't Attack, if still in EndLag.
        if (System.currentTimeMillis() - m_previousAttackTime <= m_endLag) return;

        // Ultimate Check
        if (attackType == Attack.attackType.Ultimate && m_fighter.getUltMeter() < 100) return;
        else if (attackType == Attack.attackType.Ultimate) m_fighter.setUltMeter(0);

        boolean left;
        boolean right;
        boolean up;
        boolean down;

        switch(m_controllerType) {
            case Controller: {
                left = m_controller.getAxis(SDL.SDL_CONTROLLER_AXIS_LEFTX) < -AXIS_DEADZONE;
                right = m_controller.getAxis(SDL.SDL_CONTROLLER_AXIS_LEFTX) > AXIS_DEADZONE;
                up = m_controller.getAxis(SDL.SDL_CONTROLLER_AXIS_LEFTY) > AXIS_DEADZONE;
                down = m_controller.getAxis(SDL.SDL_CONTROLLER_AXIS_LEFTY) < -AXIS_DEADZONE;
                break;
            }

            default: { // Keyboard1 binds
                left = Gdx.input.isKeyPressed(Keys.A);
                right = Gdx.input.isKeyPressed(Keys.D);
                up = Gdx.input.isKeyPressed(Keys.W);
                down = Gdx.input.isKeyPressed(Keys.S);
                break;
            }
        }

        Attack.direction direction = Attack.direction.Neutral;
        if (left || right && !(up || down)) {
            direction = Attack.direction.Side;
        }
        else if (up && !(right || down)) {
            direction = Attack.direction.Up;
        }
        else if (down && !(up || right)) {
            direction = Attack.direction.Down;
        }

        m_endLag = m_fighter.attack(attackType, direction, m_isGrounded, m_isFacingRight);
        m_previousAttackTime = System.currentTimeMillis();

        // If no Attack was done then don't play an Animation.
        if (m_endLag == 0) return;

        String attack = "";
        if (attackType == Attack.attackType.Basic)
            attack += (m_isGrounded) ? "ground" : "air";

        else if (attackType == Attack.attackType.Special)
            attack += "special";

        else if (attackType == Attack.attackType.Smash)
            attack += "smash";

        else if (attackType == Attack.attackType.Ultimate)
            attack += "ultimate";

        if (attackType != Attack.attackType.Ultimate)
            switch(direction) {
                case Neutral: {
                    attack += "Neutral";
                    break;
                }
                case Side: {
                    attack += "Side";
                    break;
                }
                case Up: {
                    attack += "Up";
                    break;
                }
                case Down: {
                    attack += "Down";
                    break;
                }
            }

        for (Fighter.Animations num : Fighter.Animations.values()) {
            if (num.path.equals(attack)) {
                m_newAnimationEnum = num;
                break;
            }
        }
    }


    public void update() {
        m_newAnimationEnum = Fighter.Animations.Idle; // Default Animation, to be overridden by others

        // Guarding Check
        if (m_isGuarding) {
            m_guardPercent -= GUARD_DEGRADE; // Degrade Shield
            m_newAnimationEnum = Fighter.Animations.Shield;
            // Shield Break
            if (m_guardPercent <= 0) {
                m_guardPercent = -GUARDBREAK_STUNTIME;
                m_isGuarding = false;
            }
        }
        // Regen Shield
        if (!m_isGuarding && m_guardPercent < 100 - GUARD_GENERATE) m_guardPercent += GUARD_GENERATE;

        // in Shield Break
        if (m_guardPercent <= 0) {
            m_newAnimationEnum = Fighter.Animations.ShieldBreak;
            return;
        }

        Body body = m_fighter.getBody();
        Vector2 pos = body.getPosition();

        m_deltaTime = System.currentTimeMillis() - m_previousTime;
        m_fallSpeed = (pos.y - m_previousY) * m_deltaTime;
        if (System.currentTimeMillis() - m_previousAttackTime <= m_endLag) return;
        m_stateTime += Gdx.graphics.getDeltaTime(); // Accumulate elapsed animation time

        // Bindings
        for (ControlAction action : m_bindings) action.checkAndPerform();
        if (m_isGrounded) m_hasDoubleJump = true;

        m_previousY = pos.y;
        m_previousTime = System.currentTimeMillis();
        setAnimation(m_newAnimationEnum);
    }


    public Fighter getFighter() {
        return m_fighter;
    }

    public void setGrounded(boolean value) {
        m_isGrounded = value;
    }

    public boolean isGuarding() {
        return m_isGuarding;
    }

    public float getGuardPercent() {
        return m_guardPercent;
    }

    public boolean isFacingRight() {
        return m_isFacingRight;
    }

    public Animation<TextureRegion> getCurrentAnimation() {
        return m_currentAnimation;
    }

    public float getStateTime() {
        return m_stateTime;
    }

    private void setAnimation(Fighter.Animations animation) {
        // Don't change anything if it's already that animation
        if (m_currentAnimationEnum == animation) return;

        // Higher Priority animations will continue until they finish.
        if (m_currentAnimationEnum.priority >= animation.priority && !m_currentAnimation.isAnimationFinished(m_stateTime)) return;

        m_stateTime = 0;
        m_currentAnimation = m_animations.get(animation);
        m_currentAnimationEnum = animation;
    }
}
