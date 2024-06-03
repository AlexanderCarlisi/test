package com.mygdx.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Box2DDebugRenderer;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.mygdx.game.PlayerController.ControllerType;

/**
 *
 * MyGdxGame class
 */
public class MyGdxGame extends ApplicationAdapter {

	public enum entityCategory {
		Default((short) 0),
		Ground((short) 1),
		Fighter((short) 2),
		Attack((short) 3);

		private final short id;
		private entityCategory(short id) {
			this.id = id;
		}

		public short getID() {
			return this.id;
		}
	}

	private static final float TIME_STEP = 1/60f;
    private static final int VELOCITY_ITERATIONS = 6;
    private static final int POSITION_ITERATIONS = 2;

	/** World Object, handles all Physics, needs to be declared first so bodies don't throw an error. */
    public static World WORLD;
	public static Camera CAMERA;

	/** Render vars */
    private Box2DDebugRenderer m_debugRenderer;
	private float m_accumulator = 0;
	private float m_previousTime = 0;

	private SpriteBatch m_spriteBatch;
	private ShapeRenderer m_shapeRenderer;
	private Battle m_battle;
	

	@Override
	public void create () { // Start of the Program
		WORLD = new World(new Vector2(0f, -1f), true);
		CAMERA = new OrthographicCamera(GDXHelper.PTM(1280), GDXHelper.PTM(720));
		m_debugRenderer = new Box2DDebugRenderer();

		m_spriteBatch = new SpriteBatch();
		m_shapeRenderer = new ShapeRenderer();
		m_shapeRenderer.setAutoShapeType(true);

		// Should eventually be moved to Render method once properly implemented.
		// Right now the Fighters are still hard coded in.
		CharacterSelect charSelect = new CharacterSelect();
		Fighter[] fighters = charSelect.isFinished();

		ControllerType[] controllers = new ControllerType[] {ControllerType.Keyboard, ControllerType.Controller};
		
		m_battle = new Battle(fighters, controllers, new BattleConfig());
		WORLD.setContactListener(new Attack.AttackCollisionListener());
	}


	@Override
	public void render () { // During the Program
		ScreenUtils.clear(0, 0, 0, 1); // values range from 0-1 instead of 0-255

		float currentTime = System.nanoTime();
        physicsStep(currentTime - m_previousTime);
        m_previousTime = currentTime;

		// Remove Dead Players, or Attacks.
		Array<Body> bodies = new Array<>();
		WORLD.getBodies(bodies);
		for (Body body : bodies) {
			for (Fixture fixture : body.getFixtureList()) {
				if (fixture.getUserData() instanceof String && fixture.getUserData().equals("MARKED FOR DELETION")) {
					WORLD.destroyBody(body);
				}
				else if (fixture.getUserData() instanceof Attack.AttackInfo) {
					Attack.AttackInfo info = (Attack.AttackInfo) fixture.getUserData();
					if (System.currentTimeMillis() > info.lifeTime) {
						WORLD.destroyBody(body);
					}
//					System.out.print(System.nanoTime() + " : " + info.lifeTime);

				}
			}
		}

		CAMERA.update();
		if (!m_battle.isFinished) m_battle.update();

		// Draw Characters
		m_debugRenderer.render(WORLD, CAMERA.combined);
		m_spriteBatch.setProjectionMatrix(CAMERA.combined);
		m_shapeRenderer.setProjectionMatrix(CAMERA.combined);

		if (!m_battle.isFinished) m_battle.draw(m_spriteBatch, m_shapeRenderer);

		// Draw Background


		// Draw UI


		// m_frameCount++;
	}

	
	@Override
	public void dispose () { // End of the Program
		m_spriteBatch.dispose();
		m_debugRenderer.dispose();
		m_battle.dispose();
	}

	
	/**
	 * 
	 * @param deltaTime 
	 */
    private void physicsStep(float deltaTime) {
        // fixed time step
        // max frame time to avoid spiral of death (on slow devices)
        float frameTime = Math.min(deltaTime, 0.25f);
        m_accumulator += frameTime;
        while (m_accumulator >= TIME_STEP) {
            WORLD.step(TIME_STEP, VELOCITY_ITERATIONS, POSITION_ITERATIONS);
            m_accumulator -= TIME_STEP;
        }
    }
}
