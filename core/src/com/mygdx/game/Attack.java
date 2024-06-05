package com.mygdx.game;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;

public class Attack {

    public static class AttackCollisionListener implements ContactListener {

        @Override
        public void beginContact(Contact contact) {
            // Fixture A is Fighter contacted with
            // Fixture B is AttackInfo
            if (contact.getFixtureA().getUserData() instanceof Fighter && contact.getFixtureB().getUserData() instanceof AttackInfo) {
                AttackInfo attackInfo = (AttackInfo) contact.getFixtureB().getUserData();
                Fighter target = (Fighter) contact.getFixtureA().getUserData();

                if (attackInfo.user != target) {
                    target.setHealth(target.getHealth() + attackInfo.attack.m_damage);

                    // Apply an impulse to the target's body in the calculated direction
                    float impulseMagnitude = (target.getHealth() / 100 / target.getWeight()) * (attackInfo.attack.m_force);
                    Vector2 impulse = new Vector2(impulseMagnitude, impulseMagnitude);

                    switch(attackInfo.attack.dir) {
                        case Neutral:

                        case Side: {
                            if (attackInfo.attack.isFacingRight) {
                                impulse.set(impulse.x, impulse.y / 2);
                            } else {
                                impulse.set(-impulse.x, impulse.y / 2);
                            }
                            break;
                        }

                        case Up: {
                            if (attackInfo.attack.isFacingRight) {
                                impulse.set(impulse.x / 4, impulse.y * 1.5f);
                            } else {
                                impulse.set(-impulse.x / 4, impulse.y * 1.5f);
                            }
                            break;
                        }

                        case Down: {
                            if (attackInfo.attack.isFacingRight) {
                                impulse.set(impulse.x / 4, -impulse.y * 1.5f);
                            } else {
                                impulse.set(-impulse.x / 4, -impulse.y * 1.5f);
                            }
                            break;
                        }
                    }

                    target.getBody().applyLinearImpulse(impulse, target.getBody().getWorldCenter(), true);

                    attackInfo.attack.dispose();
                }
            }
        }

        @Override
        public void endContact(Contact contact) {}

        @Override
        public void preSolve(Contact contact, Manifold oldManifold) {
            // Have Collision Detection, but no Physical Collisions for Attacks
            if (contact.getFixtureB().getUserData() instanceof AttackInfo) {
                contact.setEnabled(false);
            }
        }

        @Override
        public void postSolve(Contact contact, ContactImpulse impulse) {}
    }


    public static class AttackInfo {
        public Fighter user;
        public Attack attack;
        public long lifeTime;

        public AttackInfo(Fighter fighter, Attack _attack, long time) {
            user = fighter;
            attack = _attack;
            lifeTime = System.currentTimeMillis() + time;
        }
    }


    public enum direction {
        Neutral,
        Side,
        Up,
        Down
    }

    public enum attackType {
        Basic,
        Special,
        Smash,
        Ultimate
    }

    private final float m_damage;
    private final Fixture m_fixture;
    private final Body m_body;
    private final float m_force;
    private final direction dir;
    private final boolean isFacingRight;


    public Attack(Fighter user, float damage, float force, Vector2 pos, Vector2 size, direction dir, boolean isFacingRight) {
        m_damage = damage;
        m_body = MyGdxGame.WORLD.createBody(GDXHelper.generateBodyDef(BodyDef.BodyType.DynamicBody, pos));
        m_fixture = m_body.createFixture(GDXHelper.generateFixtureDef(0, 0, 0, size.x, size.y,
                MyGdxGame.entityCategory.Attack.getID(), MyGdxGame.entityCategory.Fighter.getID()));
        m_fixture.setSensor(true);
        m_fixture.setUserData(new AttackInfo(user, this, 50));
        m_force = force;
        this.dir = dir;
        this.isFacingRight = isFacingRight;
        m_body.setGravityScale(0);
    }


    public Attack(Fighter user, float damage, float force, long lifeTime, boolean bringFighter, Vector2 startingPos, Vector2 impulse, Vector2 size, direction dir, boolean isFacingRight) {
        m_damage = damage;
        m_body = MyGdxGame.WORLD.createBody(GDXHelper.generateBodyDef(BodyDef.BodyType.DynamicBody, startingPos));
        m_fixture = m_body.createFixture(GDXHelper.generateFixtureDef(0, 0, 0, size.x, size.y,
            MyGdxGame.entityCategory.Attack.getID(), MyGdxGame.entityCategory.Fighter.getID()));
        m_fixture.setSensor(true);
        m_fixture.setUserData(new AttackInfo(user, this, lifeTime));
        m_force = force;
        this.dir = dir;
        this.isFacingRight = isFacingRight;
        m_body.setGravityScale(0);

        m_body.setGravityScale(0);
        Vector2 updatedImpulse = new Vector2(isFacingRight ? impulse.x : -impulse.x, impulse.y);
        m_body.applyLinearImpulse(updatedImpulse, startingPos, true);

        if (bringFighter) {
            user.getBody().applyLinearImpulse(updatedImpulse, startingPos, true);
        }
    }

    public void dispose() {
         m_fixture.setUserData("MARKED FOR DELETION");
    }
}
